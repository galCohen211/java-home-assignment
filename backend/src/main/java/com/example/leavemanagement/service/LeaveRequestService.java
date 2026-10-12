package com.example.leavemanagement.service;

import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.exception.ConflictException;
import com.example.leavemanagement.exception.InvalidLeaveRequestException;
import com.example.leavemanagement.exception.NotFoundException;
import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class LeaveRequestService {

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public LeaveRequestService(EmployeeRepository employeeRepository,
                               LeaveRequestRepository leaveRequestRepository) {
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    // Newest first.
    @Transactional(readOnly = true)
    public List<LeaveRequest> getAll() {
        return leaveRequestRepository.findAllByOrderByStartDateDesc();
    }

    @Transactional
    public LeaveRequest create(CreateLeaveRequestDto dto) {
        Employee employee = employeeRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new NotFoundException("Employee not found"));

        // Calendar days, inclusive of both ends.
        int days = (int) ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()) + 1;

        if (dto.getType() == LeaveType.VACATION
                && usedVacationDays(employee.getId()) + days > employee.getAnnualQuota()) {
            throw new InvalidLeaveRequestException("Not enough vacation balance");
        }

        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(employee.getId());
        request.setType(dto.getType());
        request.setStartDate(dto.getStartDate());
        request.setEndDate(dto.getEndDate());
        request.setDays(days);
        request.setStatus(LeaveStatus.PENDING);

        return leaveRequestRepository.save(request);
    }

    // Lock the employee first, then check status and balance,
    // so two approvals at the same time can't both pass
    @Transactional
    public LeaveRequest approve(Long requestId) {
        Long employeeId = leaveRequestRepository.findEmployeeIdById(requestId)
                .orElseThrow(() -> new NotFoundException("Leave request not found"));

        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));

        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Leave request not found"));

        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new ConflictException("Only pending requests can be approved");
        }

        if (request.getType() == LeaveType.VACATION
                && usedVacationDays(employeeId) + request.getDays() > employee.getAnnualQuota()) {
            throw new ConflictException("Not enough vacation balance");
        }

        // No save() needed: JPA saves the change automatically when the transaction ends
        request.setStatus(LeaveStatus.APPROVED);
        return request;
    }

    private int usedVacationDays(Long employeeId) {
        return leaveRequestRepository
                .findByEmployeeIdAndTypeAndStatus(employeeId, LeaveType.VACATION, LeaveStatus.APPROVED)
                .stream()
                .mapToInt(LeaveRequest::getDays)
                .sum();
    }
}
