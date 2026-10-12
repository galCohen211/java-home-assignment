package com.example.leavemanagement.service;

import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaveRequestService {

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public LeaveRequestService(EmployeeRepository employeeRepository,
                               LeaveRequestRepository leaveRequestRepository) {
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    // Lock the employee first, then check status and balance,
    // so two approvals at the same time can't both pass
    @Transactional
    public LeaveRequest approve(Long requestId) {
        Long employeeId = leaveRequestRepository.findEmployeeIdById(requestId)
                .orElseThrow(() -> new LeaveRequestNotFoundException("Leave request not found"));

        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new LeaveRequestNotFoundException("Employee not found"));

        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new LeaveRequestNotFoundException("Leave request not found"));

        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new LeaveRequestConflictException("Only pending requests can be approved");
        }

        if (request.getType() == LeaveType.VACATION) {
            int used = leaveRequestRepository
                    .findByEmployeeIdAndTypeAndStatus(employeeId, LeaveType.VACATION, LeaveStatus.APPROVED)
                    .stream()
                    .mapToInt(LeaveRequest::getDays)
                    .sum();

            if (used + request.getDays() > employee.getAnnualQuota()) {
                throw new LeaveRequestConflictException("Not enough vacation balance");
            }
        }

        // No save() needed: JPA saves the change automatically when the transaction ends
        request.setStatus(LeaveStatus.APPROVED);
        return request;
    }
}
