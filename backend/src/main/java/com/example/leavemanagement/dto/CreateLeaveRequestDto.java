package com.example.leavemanagement.dto;

import com.example.leavemanagement.model.LeaveType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

// Incoming payload for creating a leave request.
public class CreateLeaveRequestDto {

    @NotNull(message = "employeeId is required")
    private Long employeeId;

    @NotNull(message = "type is required")
    private LeaveType type;

    @NotNull(message = "startDate is required")
    private LocalDate startDate;

    @NotNull(message = "endDate is required")
    private LocalDate endDate;

    // Missing dates are reported by @NotNull above, so only check a complete range.
    @JsonIgnore
    @AssertTrue(message = "startDate must be on or before endDate")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !startDate.isAfter(endDate);
    }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public LeaveType getType() { return type; }
    public void setType(LeaveType type) { this.type = type; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}
