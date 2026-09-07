// RestProject.java

private void updateProjectGeneral(Employee current, Project project, Map<String, Object> model) {
    if (model.get("statusChildren") != null) {
        boolean withChildren = (Boolean) model.get("statusChildren");
        project.setStatus(current, ProjectStatus.valueOf((String) model.get("status")), withChildren);
    }

    if (model.get("completion") != null) {
        Calendar utc = DateHelper.getCalendarUTC();
        DateHelper.stringToCalendar((String) model.get("completion"), utc);
        project.setCompletionDate(utc.getTime());
    } else {
        project.setCompletionDate(null);
    }

    if (model.get("startDate") != null) {
        Calendar utc = DateHelper.getCalendarUTC();
        DateHelper.stringToCalendar((String) model.get("startDate"), utc);
        project.setStartDate(utc.getTime());
    } else {
        project.setStartDate(null);
    }

    if (model.get("startTime") != null) {
        project.setStartTime((int) Math.round(Utils.getDouble(model.get("startTime")) * 60));
        project.checkTargetTime(false);
    }

    if (project.getType() == Project.Type.CUSTOMER || project.getType() == Project.Type.PROJECT || project.getType() == Project.Type.CONTACT) {
        Address address = project.getAddress();
        address.update(model);
    }

    if (project.getType() != Project.Type.CUSTOMER) {
        long targetTime = Math.round(Utils.getDouble(model.get("targetTime")) * 60);
        project.setTargetTime((int) targetTime);
    }

    if (model.get("sgb") != null) {
        project.setSgb(Project.SGB.valueOf((String) model.get("sgb")));
    }

    project.setCostCentre((String) model.get("costCentre"));

    project.setName((String) model.get("name"));
    try {
        project.setLabel((String) model.get("label"));
    } catch (DuplicateException e) {
        throw new DuplicateException(CompanyType.PROJECT);
    }
    project.setDescription((String) model.get("description"));
    // --------------------- add begin
    if (model.get("projectNumber") != null) {
        String projectNumber = ((String) model.get("projectNumber")).trim();
        if (projectNumber.length() > 255) {
            throw new BadRequestException("Project number cannot exceed 255 characters.");
        }

        PatternType patternType = current.getCompany().getProjectManagementCompany().getProjectNumberPatternType();
        if (!projectNumber.isEmpty() && !patternType.matches(projectNumber)) {
            throw new BadRequestException("Project number does not match the required pattern.");
        }

        project.setProjectNumber(projectNumber);
    }
    // ---------------------- end add
    if (project.getType() == Project.Type.PROJECT) {
        project.setRound((Integer) model.get("round"));
    }
}

// ...

map.put("sort", project.getSortValue(true));
map.put("label", project.getLabel());
// --------------------- add begin
PatternType patternType = current.getCompany().getProjectManagementCompany().getProjectNumberPatternType();
map.put("projectNumberPatternType", patternType.name());
map.put("projectNumberRegex", patternType.getPattern());
// ---------------------- end add
map.put("url", project.getUrl());


// ProjectManagementCompany.java

// --------------------- add begin
public PatternType getProjectNumberPatternType() {
    return projectNumberPatternType;
}

public void setProjectNumberPatternType(PatternType projectNumberPatternType) {
    this.projectNumberPatternType = projectNumberPatternType;
}
// ---------------------- end add

// RESTCompany.java

project.put("restrictedEmployees", projectCompany.isRestrictedEmployees());
project.put("gitlabToken", projectCompany.getGitLabToken());
// --------------------- add begin
project.put("projectNumberPatternType", projectCompany.getProjectNumberPatternType().name());
// ---------------------- end add
TimeRegistrationCompany timeCompany = company.getTimeRegistrationCompany();

// ...

Boolean allowFavourites = (Boolean) project.get("allowFavourites");
if (allowFavourites != null) {
    projectCompany.setAllowFavourites(allowFavourites);
}
projectCompany.setReuseComment((Boolean) project.get("reuseComment"));
projectCompany.setGitLabToken((String) project.get("gitlabToken"));

// --------------------- add begin
String projectNumberPatternType = (String) project.get("projectNumberPatternType");
projectCompany.setProjectNumberPatternType(PatternType.valueOf(projectNumberPatternType));
// ---------------------- end add

boolean allowService = projectCompany.getAllowServices();
projectCompany.setMandatoryService((Boolean) project.get("mandatoryService"));


// project.ts 

// --------------------- add begin
projectNumberPatternType?: string;
projectNumberRegex?: string;
// ---------------------- end add