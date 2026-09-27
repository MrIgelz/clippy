package de.saas.server.helper.report;

import de.saas.core.base.CustomCell;
import de.saas.core.base.ExcelGenerator;
import de.saas.core.data.Company;
import de.saas.core.data.Department;
import de.saas.core.data.Employee;
import de.saas.core.data.leave.LeaveManagerHoliday;
import de.saas.core.data.leave.LeaveManagerType;
import de.saas.core.data.timeregistration.TimeRegistrationEmployee;
import de.saas.core.data.timeregistration.TimeRegistrationEntry;
import de.saas.core.data.timeregistration.TimeRegistrationMonthlySummary;
import de.saas.nls.NLSHelper;
import de.saas.server.helper.DateHelper;
import de.saas.server.helper.DateTimeFormatHelper;
import de.saas.server.helper.DownloadHelper;
import de.saas.shared.core.data.CompanyType;
import de.saas.shared.core.data.EntryStatus;
import de.saas.shared.core.data.PaymentModule;
import de.saas.shared.exceptions.AuthException;
import de.saas.shared.exceptions.BrokenSessionException;
import de.saas.shared.exceptions.PermissionDeniedException;
import jakarta.ws.rs.core.Response;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.*;

// ADD TO ExcelGenerator.java
/*
 * Green Background
 */
/*
XSSFCellStyle greenBg = workbook.createCellStyle();
XSSFColor greenColor = new XSSFColor(new byte[]{
	(byte) 0xC6,
	(byte) 0xEF,
	(byte) 0xCE
}, null);
greenBg.setFillForegroundColor(greenColor);
greenBg.setFillPattern(FillPatternType.SOLID_FOREGROUND);
this.styleMap.put("greenBg", greenBg);
*/

// REMOVE "&& false": if (current.getCompany().getCustomerId() != ALLOWED_CUSTOMER_ID && false) {

public class MonthXLSReportCustomer32 {

	private static final Integer ALLOWED_CUSTOMER_ID = 32;

	private static final String SERVICE_UUID_ANFAHRT = "d469edfe-1d42-4a7a-9098-d8fb0003e0cd";
	private static final String SERVICE_UUID_ABFAHRT = "60183a1a-20cf-417d-82ad-73d98dd8df2b";

	private static final String LEAVE_TYPE_UUID_NOTDIENST = "3cc1fd21e16411e594440201d2bce47d";
	private static final String LEAVE_TYPE_UUID_ERHOLUNGSBEIHILFE = "aa65834d-5c2b-40a7-bc6e-ce0a47cf70f2";

	private static final DateTimeFormatter W_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM");
	private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yy");
	private static final String[] NLS_MONTH_KEYS = {
		"januar", "februar", "maerz", "april", "mai", "juni",
		"juli", "august", "september", "oktober", "november", "dezember"
	};

	// FLAG: Controls whether night work 2 and 3 are included
	public static final boolean showExtendedNightWork = true;
	// FLAG: Controls whether emergency service is shown as calendar weeks ("KW 22 ...") or as a count
	public static final boolean showEmergencyServiceCalendarWeeks = true;

	private Department department;
	private final Employee current;
	private boolean checkLicence;

	private boolean surchargeActive;
	private boolean overtimeAccountActive;
	private boolean allowService;

	private Calendar from;
	private Calendar until;
	private int selectedYear;
	private int selectedMonth;

	private NLSHelper nls;
	private String filename;

	public MonthXLSReportCustomer32(
		Employee current,
		String uuid,
		String _from,
		String _until,
		boolean checkLicence
	) {
		this.current = current;
		this.checkLicence = checkLicence;

		auth(uuid);
		initialize(_from, _until);
	}

	public Response download() throws IOException  {
		ByteArrayOutputStream out = generateExcel();
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		return DownloadHelper.download(in, filename + ".xlsx", null, false);
	}

	private ByteArrayOutputStream generateExcel() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ExcelGenerator excelGenerator = new ExcelGenerator(out, current);
		excelGenerator.addSheet(nls.getConstant("mitarbeiter_singular"));

		printHeader(excelGenerator);
		printContent(excelGenerator);

		return excelGenerator.closeOutput(true);
	}

	private void auth(String uuid) {
		if (current == null) {
			throw new BrokenSessionException();
		} else if (!current.hasModule(PaymentModule.TIME_REGISTRATION)) {
			throw new AuthException(AuthException.Type.MODULE);
		} else if (!current.isCEO() && !current.isOfficer(CompanyType.EMPLOYEE) && !current.isExport()) {
			throw new BrokenSessionException();
		}

		if (current.getCompany().getCustomerId() != ALLOWED_CUSTOMER_ID && false) {
			throw new PermissionDeniedException();
		}

		if (uuid == null) {
			throw new IllegalArgumentException("wrong params");
		}

		department = Department.getByExternID(uuid);
		if (!current.checkDepartment(department, PaymentModule.TIME_REGISTRATION, false, false, true)) {
			throw new PermissionDeniedException();
		}

		if (!current.isCEO() && !current.isOfficer(CompanyType.COMPANY)) {
			checkLicence = true;
		}
	}

	private void initialize(String _from, String _until) {
		Company company = current.getCompany();
		surchargeActive = company.getTimeRegistrationCompany().isSurcharge();
		overtimeAccountActive = company.getTimeRegistrationCompany().getShowFlexTime();
		allowService = company.getProjectManagementCompany().getAllowServices();

		from = DateHelper.stringToCalendar(_from, company.getCalendar());
		until = DateHelper.stringToCalendar(_until, company.getCalendar());
		until.add(Calendar.DAY_OF_YEAR, 1);
		until.add(Calendar.SECOND, -1);

		Calendar helper = DateHelper.clearCalendar(company.getCalendar());
		if (until.after(helper)) {
			until.setTime(helper.getTime());
			_until = DateHelper.convertToString(until.getTime(), false);
		}

		this.selectedYear = from.get(Calendar.YEAR);
		this.selectedMonth = from.get(Calendar.MONTH);

		nls = new NLSHelper(current.getLanguage());

		String d1 = DateTimeFormatHelper.dateToStringLocale(DateHelper.stringToCalendar(_from, DateHelper.getCalendarUTC()).getTime(), current.getDateFormat());
		String d2 = DateTimeFormatHelper.dateToStringLocale(DateHelper.stringToCalendar(_until, DateHelper.getCalendarUTC()).getTime(), current.getDateFormat());
		filename = "Lohndatenuebergabe__" + d1 + " " + d2;
	}

	private void printHeader(ExcelGenerator excelGenerator) {
		List<String> header = new ArrayList<>();
		header.add(nls.getConstant("name"));
		header.add(nls.getConstant("personalnummer"));
		if (surchargeActive) {
			header.add(nls.getConstant("zuschlag") + "(" + nls.getConstant("SaturdayShort") + ")");
			header.add(nls.getConstant("zuschlag") + "(" + nls.getConstant("SundayShort") + ")");
			header.add(nls.getConstant("zuschlag") + "(" + nls.getConstant("nachtarbeit") + " 1)");
			if (showExtendedNightWork) {
				header.add(nls.getConstant("zuschlag") + "(" + nls.getConstant("nachtarbeit") + " 2)");
				header.add(nls.getConstant("zuschlag") + "(" + nls.getConstant("nachtarbeit") + " 3)");
			}
		}
		if (overtimeAccountActive) {
			header.add("Auszahlung Überstunden");
		}
		if (allowService) {
			header.add("Auszahlung Fahrzeit");
		}
		header.add("Notdienst");
		header.add("Erholungsbeihilfe");

		excelGenerator.writeHeader(header, 0);
	}

	private void printContent(ExcelGenerator excelGenerator) {
		List<Employee> employees = department.getAllEmployeesByModulAndEmployee(current, checkLicence ? PaymentModule.TIME_REGISTRATION : null, false, true, true);
		Collections.sort(employees);

		String selectedMonthLabel = formatMonthYear(YearMonth.of(this.selectedYear, this.selectedMonth + 1));

		for (Employee employee : employees) {
			if (!employee.isActive(from, until)) {
				continue;
			}

			TimeRegistrationEmployee trEmployee = employee.getTimeRegistrationEmployee();
			if (!trEmployee.isActive(until)) {
				continue;
			}

			TimeRegistrationMonthlySummary summary = trEmployee.getMonthlySummaryActive(selectedYear, selectedMonth);
			if (summary == null) {
				continue;
			}

			List<CustomCell> line = new ArrayList<>();
			// Name
			line.add(new CustomCell(employee.getFullName(), null));
			// Personnel Number
			line.add(new CustomCell(employee.getPersonnelNumber(), null));

			// Surcharges
			if (surchargeActive) {
				// Surcharge Saturday
				line.add(new CustomCell(getSaturdaySurchargeAsDecimalHours(employee), null));
				List<Integer> surcharges = summary.getSurchargeContent();
				// Surcharge Sunday
				line.add(new CustomCell(getSurchargeAsDecimalHours(surcharges, 1), null));
				// Surcharge Night Work 1
				line.add(new CustomCell(getSurchargeAsDecimalHours(surcharges, 3), null));
				if (showExtendedNightWork) {
					// Surcharge Night Work 2
					line.add(new CustomCell(getSurchargeAsDecimalHours(surcharges, 4), null));
					// Surcharge Night Work 3
					line.add(new CustomCell(getSurchargeAsDecimalHours(surcharges, 5), null));
				}
			}

			// Overtime Payoff
			if (overtimeAccountActive) {
				line.add(new CustomCell(getOvertimePayoffAsDecimalHours(employee), null));
			}

			// Travel Time Payoff
			if (allowService) {
				line.add(new CustomCell(getTravelTimePayoffAsDecimalHours(employee), null));
			}

			// Emergency Service
			Set<String> emergencyWeeks = getEmergencyServiceWeeks(employee);
			Object emergencyCellValue = showEmergencyServiceCalendarWeeks
				? String.join("\n", emergencyWeeks)
				: emergencyWeeks.size();
			String emergencyCellStyle = !emergencyWeeks.isEmpty() ? "greenBg" : null;

			line.add(new CustomCell(emergencyCellValue, emergencyCellStyle));

			// Recuperation Allowance
			List<String> recuperationMonths = getRecuperationAllowance(employee);
			String recuperationCellStyle = recuperationMonths.contains(selectedMonthLabel) ? "greenBg" : null;

			line.add(new CustomCell(String.join("\n", recuperationMonths), recuperationCellStyle));

			excelGenerator.writeLine(line, 0);
		}
	}

	private float getSaturdaySurchargeAsDecimalHours(Employee employee) {
		ZoneId companyZone = employee.getCompany().getCalendar().getTimeZone().toZoneId();

		Date startDate = this.from.getTime();
		Date endDate = this.until.getTime();

		List<TimeRegistrationEntry> list = TimeRegistrationEntry.getEntriesByDateAndEmployee(
			startDate, endDate, employee, false, false, false);

		int saturdayMinutes = 0;

		for (TimeRegistrationEntry entry : list) {
			LocalDate date = entry.getStart().toInstant().atZone(companyZone).toLocalDate();

			if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
				saturdayMinutes += entry.getLengthTime();
			}
		}

		return (float) (saturdayMinutes / 60.0);
	}

	private double getSurchargeAsDecimalHours(List<Integer> surcharges, int index) {
		if (surcharges == null || surcharges.size() <= index) {
			return 0.0;
		}
		return Math.round(surcharges.get(index) / 60d * 100) / 100.0;
	}

	private float getOvertimePayoffAsDecimalHours(Employee employee) {
		int overtimeTodayMinutes = employee.getTimeRegistrationEmployee().calcOvertimeAccount(null, null, true).remainingOvertime;
		// 80 hours = 4800 minutes. Anything above that is paid out.
		int payoffMinutes = Math.max(0, overtimeTodayMinutes - 4800);
		return (float) (payoffMinutes / 60.0);
	}

	private float getTravelTimePayoffAsDecimalHours(Employee employee) {
		ZoneId companyZone = employee.getCompany().getCalendar().getTimeZone().toZoneId();

		Date startDate = this.from.getTime();
		Date endDate = this.until.getTime();

		List<TimeRegistrationEntry> list = TimeRegistrationEntry.getEntriesByDay(
			startDate, endDate, employee, false, false);

		Map<LocalDate, Integer> anfahrtPerDay = new HashMap<>();
		Map<LocalDate, Integer> abfahrtPerDay = new HashMap<>();

		for (TimeRegistrationEntry entry : list) {
			if (entry.getService() == null) {
				continue;
			}

			String serviceUuid = entry.getService().getExternID();
			if (Objects.equals(serviceUuid, SERVICE_UUID_ANFAHRT) || Objects.equals(serviceUuid, SERVICE_UUID_ABFAHRT)) {

				Date referenceDate = entry.getDay() != null ? entry.getDay() : entry.getStart();
				LocalDate workDay = referenceDate.toInstant().atZone(companyZone).toLocalDate();

				int duration = entry.getLengthTime();

				if (Objects.equals(serviceUuid, SERVICE_UUID_ANFAHRT)) {
					anfahrtPerDay.merge(workDay, duration, Integer::sum);
				} else {
					abfahrtPerDay.merge(workDay, duration, Integer::sum);
				}
			}
		}

		int totalPayoffMinutes = 0;

		Set<LocalDate> allWorkDays = new HashSet<>();
		allWorkDays.addAll(anfahrtPerDay.keySet());
		allWorkDays.addAll(abfahrtPerDay.keySet());

		for (LocalDate workDay : allWorkDays) {
			int anfahrtMins = anfahrtPerDay.getOrDefault(workDay, 0);
			int abfahrtMins = abfahrtPerDay.getOrDefault(workDay, 0);

			totalPayoffMinutes += Math.max(0, anfahrtMins - 60);
			totalPayoffMinutes += Math.max(0, abfahrtMins - 60);
		}

		return (float) (totalPayoffMinutes / 60.0);
	}

	private Set<String> getEmergencyServiceWeeks(Employee employee) {
		YearMonth targetMonth = YearMonth.of(this.selectedYear, this.selectedMonth + 1);

		List<LeaveManagerHoliday> leaves = LeaveManagerHoliday.getByEmployeeAndStartMonth(
			employee, targetMonth, false);

		Set<String> emergencyServiceWeeks = new LinkedHashSet<>();

		for (LeaveManagerHoliday leave : leaves) {
			if (leave.getStatus() == EntryStatus.ACCEPTED && Objects.equals(leave.getLeaveType().getExternID(), LEAVE_TYPE_UUID_NOTDIENST)) {

				LocalDate firstDay = leave.getStart().toInstant().atZone(ZoneOffset.UTC).toLocalDate();
				LocalDate monday = firstDay.with(DayOfWeek.MONDAY);
				LocalDate sunday = firstDay.with(DayOfWeek.SUNDAY);
				int calendarWeek = firstDay.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

				String label = String.format(
					"KW %02d %s - %s",
					calendarWeek,
					monday.format(W_DATE_FORMAT),
					sunday.format(W_DATE_FORMAT)
				);

				emergencyServiceWeeks.add(label);
			}
		}

		return emergencyServiceWeeks;
	}

	private List<String> getRecuperationAllowance(Employee employee) {
		LeaveManagerType type = LeaveManagerType.getByExternID(LEAVE_TYPE_UUID_ERHOLUNGSBEIHILFE);
		if (type == null) {
			return new ArrayList<>();
		}

		List<String> monthsWithAllowance = new ArrayList<>();
		YearMonth baseMonth = YearMonth.of(this.selectedYear, this.selectedMonth + 1);

		for (int i = 0; i <= 5; i++) {
			YearMonth targetMonth = baseMonth.minusMonths(i);

			List<LeaveManagerHoliday> leaves = LeaveManagerHoliday.getByEmployeeAndStartMonth(
				employee, targetMonth, false);

			for (LeaveManagerHoliday leave : leaves) {
				if (leave.getStatus() == EntryStatus.ACCEPTED && Objects.equals(leave.getLeaveType().getExternID(), LEAVE_TYPE_UUID_ERHOLUNGSBEIHILFE)) {
					monthsWithAllowance.add(formatMonthYear(targetMonth));
					break;
				}
			}
		}

		return monthsWithAllowance;
	}

	private String formatMonthYear(YearMonth month) {
		String fullMonth = nls.getConstant(NLS_MONTH_KEYS[month.getMonthValue() - 1]);
		String shortMonth = fullMonth.length() > 3 ? fullMonth.substring(0, 3) : fullMonth;
		return shortMonth + ". " + month.format(YEAR_FORMAT);
	}
}
