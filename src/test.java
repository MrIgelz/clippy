package de.saas.server.rest;

import de.saas.core.base.ThreadState;
import de.saas.core.data.Company;
import de.saas.core.data.Employee;
import de.saas.core.data.tasks.time.TaskDATEVLohnSurchage;
import de.saas.core.data.tasks.time.TaskExportTimeMonth;
import de.saas.core.data.tasks.time.TaskExportTimeMonth2;
import de.saas.core.data.tasks.time.TaskExportTimeSheet;
import de.saas.helper.RESTTestHelper;
import de.saas.nls.NLSHelper;
import de.saas.server.helper.report.MonthXLSReportCustomer32;
import de.saas.shared.core.data.PaymentModule;
import de.saas.shared.exceptions.BrokenSessionException;
import de.saas.shared.exceptions.PermissionDeniedException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RESTExportTimeTest extends RESTTestHelper {

	@Test
	void datevQueuesSurchargeTask() {
		ensureTimeModule(ceo);
		RESTExportTimeTest rest = mockApi(new RESTExportTimeTest(), mockSessionCeo());

		int initial = findTasks(TaskDATEVLohnSurchage.class).size();

		HashMap<String, Object> data = new HashMap<>();
		data.put("uuid", department.getExternID());
		data.put("type", 4096);
		data.put("year", 2024);
		data.put("month", 0);

		Map<String, Object> result = rest.datev(data);
		assertEquals(true, result.get("success"), "DATEV endpoint should return success");
		assertEquals(initial + 1, findTasks(TaskDATEVLohnSurchage.class).size(), "DATEV endpoint should enqueue surcharge task");
	}

	@Test
	void exportDepartmentQueuesCorrectTask() {
		ensureTimeModule(ceo);
		RESTExportTimeTest rest = mockApi(new RESTExportTimeTest(), mockSessionCeo());

		HashMap<String, Object> baseData = new HashMap<>();
		baseData.put("selected", department.getExternID());
		baseData.put("start", "01.01.2024");
		baseData.put("until", "01.01.2025");
		baseData.put("checkLicence", true);

		baseData.put("type", 64);
		int initialMonth2 = findTasks(TaskExportTimeMonth2.class).size();
		rest.exportDepartment(new HashMap<>(baseData));
		assertEquals(initialMonth2 + 1, findTasks(TaskExportTimeMonth2.class).size(), "Type 64 should enqueue TaskExportTimeMonth2 (month)");

		baseData.put("type", 2048);
		rest.exportDepartment(new HashMap<>(baseData));
		assertEquals(initialMonth2 + 2, findTasks(TaskExportTimeMonth2.class).size(), "Type 2048 should enqueue TaskExportTimeMonth2 (range)");

		baseData.put("type", 128);
		baseData.put("typeSpecial", "STANDARD");
		baseData.put("singleFile", true);
		int initialMonth = findTasks(TaskExportTimeMonth.class).size();
		rest.exportDepartment(new HashMap<>(baseData));
		assertEquals(initialMonth + 1, findTasks(TaskExportTimeMonth.class).size(), "Type 128 should enqueue TaskExportTimeMonth");

		baseData.put("type", 256);
		int initialSheet = findTasks(TaskExportTimeSheet.class).size();
		rest.exportDepartment(new HashMap<>(baseData));
		assertEquals(initialSheet + 1, findTasks(TaskExportTimeSheet.class).size(), "Type 256 should enqueue TaskExportTimeSheet");
	}

	@Test
	void exportEmployeeQueuesSheet() {
		ensureTimeModule(ceo);
		RESTExportTimeTest rest = mockApi(new RESTExportTimeTest(), mockSessionCeo());

		int initialSheet = findTasks(TaskExportTimeSheet.class).size();

		HashMap<String, Object> data = new HashMap<>();
		data.put("selected", employee.getExternID());
		data.put("year", 2024);
		data.put("month", 0);
		data.put("type", 256);

		Map<String, Object> result = rest.exportEmployee(data);
		assertEquals(true, result.get("success"), "Export employee should return success");
		assertEquals(initialSheet + 1, findTasks(TaskExportTimeSheet.class).size(), "Export employee should enqueue TaskExportTimeSheet");
	}

	@Test
	void forbidden() {
		HashMap<String, Object> data = new HashMap<>();
		data.put("uuid", department.getExternID());
		data.put("selected", department.getExternID());
		data.put("start", "01.01.2024");
		data.put("until", "01.02.2025");
		data.put("checkLicence", true);
		data.put("type", 4096);
		data.put("year", 2024);
		data.put("month", 0);

		RESTExportTimeTest restEmployee = mockApi(new RESTExportTimeTest(), mockSessionEmployee());
		assertThrows(PermissionDeniedException.class, () -> restEmployee.datev(data), "Employee should not queue DATEV export");
		assertThrows(PermissionDeniedException.class, () -> restEmployee.exportDepartment(data), "Employee should not export department time");
		assertThrows(PermissionDeniedException.class, () -> restEmployee.exportEmployee(new HashMap<>(Map.of(
				"selected", ceo.getExternID(), "year", 2024, "month", 0, "type", 256
		))), "Employee should not export other employee");

		RESTExportTimeTest restSecond = mockApi(new RESTExportTimeTest(), mockSessionSecondCeo());
		assertThrows(PermissionDeniedException.class, () -> restSecond.datev(data), "Cross-company DATEV should be denied");
		assertThrows(PermissionDeniedException.class, () -> restSecond.exportDepartment(data), "Cross-company department export should be denied");
		assertThrows(PermissionDeniedException.class, () -> restSecond.exportEmployee(new HashMap<>(Map.of(
				"selected", ceo.getExternID(), "year", 2024, "month", 0, "type", 256
		))), "Cross-company employee export should be denied");

		RESTExportTimeTest restNoSession = mockApi(new RESTExportTimeTest());
		assertThrows(BrokenSessionException.class, () -> restNoSession.datev(data), "Missing session should break");
		assertThrows(BrokenSessionException.class, () -> restNoSession.exportDepartment(data), "Missing session should break");
		assertThrows(BrokenSessionException.class, () -> restNoSession.exportEmployee(new HashMap<>(Map.of(
				"selected", ceo.getExternID(), "year", 2024, "month", 0, "type", 256
		))), "Missing session should break");

		data.put("type", 2048);
		RESTExportTimeTest rest = mockApi(new RESTExportTimeTest(), mockSessionCeo());
		WebApplicationException exception = assertThrows(
			WebApplicationException.class,
			() -> rest.exportDepartment(data)
		);

		assertEquals(
			"DATE_RANGE_LIMIT_EXCEEDED_ERROR",
			exception.getResponse().getEntity()
		);

		exception = assertThrows(
			WebApplicationException.class,
			() -> rest.download(8, "", "01.01.2023", "01.02.2024", false)
		);

		assertEquals(
			"DATE_RANGE_LIMIT_EXCEEDED_ERROR",
			exception.getResponse().getEntity()
		);

		exception = assertThrows(
			WebApplicationException.class,
			() -> rest.xls2("01.01.2023", "01.02.2024", employee.getExternID(), "")
		);

		assertEquals(
			"DATE_RANGE_LIMIT_EXCEEDED_ERROR",
			exception.getResponse().getEntity()
		);

	}

	@Test
	void exportMonthXLSReportCustomer32() throws Exception {
		NLSHelper nls = new NLSHelper(ceo.getLanguage());
		ensureTimeModule(ceo);

		Field field = Company.class.getDeclaredField("customerId");
		field.setAccessible(true);
		field.set(company, 32);
		company.update();

		RESTExportTime rest = mockApi(new RESTExportTime(), mockSessionCeo());

		try (Response response = assertDoesNotThrow(
				() -> rest.download(
					32,
					company.getPseudoDepartment().getExternID(),
					"2026-2-1",
					"2026-2-31",
					true
				), "Should succeed when requested by a CEO of customer 32."
		)) {
			assertEquals(200, response.getStatus(), "Response status should be 200 OK.");

			File xlsxFile = (File) response.getEntity();
			assertNotNull(xlsxFile, "Excel file in response should not be null.");
			assertTrue(xlsxFile.exists(), "Excel file must exist.");

			try (FileInputStream fis = new FileInputStream(xlsxFile);
				 XSSFWorkbook workbook = assertDoesNotThrow(
					 () -> new XSSFWorkbook(fis), "Generated file should be a valid XLSX workbook."
			)) {
				XSSFSheet sheet = workbook.getSheetAt(0);
				assertNotNull(sheet, "Workbook should contain a sheet at index 0.");
				assertEquals(nls.getConstant("mitarbeiter_singular"), sheet.getSheetName(), "Sheet name should be Mitarbeiter " + nls.getConstant("mitarbeiter_singular") + ".");

				int col = 0;
				XSSFRow header = sheet.getRow(0);

				assertEquals(nls.getConstant("name"), header.getCell(col++).getStringCellValue(), "Column " + nls.getConstant("name") + " should be at the expected position.");
				assertEquals(nls.getConstant("personalnummer"), header.getCell(col++).getStringCellValue(), "Column " + nls.getConstant("personalnummer") + " should be at the expected position.");

				if (company.getTimeRegistrationCompany().isSurcharge()) {
					assertEquals(nls.getConstant("zuschlag") + "(" + nls.getConstant("SaturdayShort") + ")", header.getCell(col++).getStringCellValue(), "Header should contain Saturday surcharge column.");
					assertEquals(nls.getConstant("zuschlag") + "(" + nls.getConstant("SundayShort") + ")", header.getCell(col++).getStringCellValue(), "Header should contain Sunday surcharge column.");
					assertEquals(nls.getConstant("zuschlag") + "(" + nls.getConstant("nachtarbeit") + " 1)", header.getCell(col++).getStringCellValue(), "Header should contain Night Work 1 surcharge column.");

					if (MonthXLSReportCustomer32.showExtendedNightWork) {
						assertEquals(nls.getConstant("zuschlag") + "(" + nls.getConstant("nachtarbeit") + " 2)", header.getCell(col++).getStringCellValue(), "Header should contain Night Work 2 surcharge column when showExtendedNightWork is enabled.");
						assertEquals(nls.getConstant("zuschlag") + "(" + nls.getConstant("nachtarbeit") + " 3)", header.getCell(col++).getStringCellValue(), "Header should contain Night Work 3 surcharge column when showExtendedNightWork is enabled.");
					}
				}

				if (company.getTimeRegistrationCompany().getShowFlexTime()) {
					assertEquals("Auszahlung Überstunden", header.getCell(col++).getStringCellValue(), "Header should contain 'Auszahlung Überstunden' when flex time is active.");
				}

				if (company.getProjectManagementCompany().getAllowServices()) {
					assertEquals("Auszahlung Fahrzeit", header.getCell(col++).getStringCellValue(), "Header should contain 'Auszahlung Fahrzeit' when services are allowed.");
				}

				assertEquals("Notdienst", header.getCell(col++).getStringCellValue(), "Second-to-last header column should be 'Notdienst'.");
				assertEquals("Erholungsbeihilfe", header.getCell(col++).getStringCellValue(), "Last header column should be 'Erholungsbeihilfe'.");

				assertEquals(col, header.getLastCellNum(), "Total number of header columns should be " + col + ".");

				XSSFRow row1 = sheet.getRow(1);
				assertNotNull(row1);

				DataFormatter formatter = new DataFormatter();
				String employeeName = formatter.formatCellValue(row1.getCell(0));
				assertEquals("Max Mustermann", employeeName);

				// Example using countTextOccurrences:
				// assertEquals(1, countTextOccurrences(sheet, "KW 28"));
			}
		}




	}

	private int countTextOccurrences(Sheet sheet, String expectedText) {
		DataFormatter formatter = new DataFormatter();
		int count = 0;

		for (Row row : sheet) {
			for (Cell cell : row) {
				String cellText = formatter.formatCellValue(cell);
				int index = 0;
				while ((index = cellText.indexOf(expectedText, index)) != -1) {
					count++;
					index += expectedText.length();
				}
			}
		}
		return count;
	}

	private void ensureTimeModule(Employee employee) {
		if (!employee.hasModule(PaymentModule.TIME_REGISTRATION)) {
			employee.getCompany().addNumLicenses(PaymentModule.TIME_REGISTRATION, 1);
			employee.getCompany().update();
			employee.setHasModule(PaymentModule.TIME_REGISTRATION, true);
			employee.update();
		}
	}

	private <T> List<T> findTasks(Class<T> type) {
		return ThreadState.em().createQuery("SELECT t FROM " + type.getSimpleName() + " t", type).getResultList();
	}
}
