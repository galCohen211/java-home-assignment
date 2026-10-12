package com.example.leavemanagement;

import com.example.leavemanagement.controller.LeaveRequestsController;
import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

// Runs against a real, throwaway PostgreSQL started by Testcontainers.
// (Docker must be available on the machine running the tests.)
@SpringBootTest
@Testcontainers
class LeaveRequestsTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private LeaveRequestsController controller;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private LeaveRequestRepository leaveRequests;

    @Test
    void create_WithinQuota_Succeeds() {
        // Arrange
        Employee emp = new Employee();
        emp.setName("Test Emp");
        emp.setAnnualQuota(20);
        employees.save(emp);

        long before = leaveRequests.count();

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(emp.getId());
        dto.setType(LeaveType.VACATION);
        dto.setStartDate(LocalDate.of(2026, 3, 1));
        dto.setEndDate(LocalDate.of(2026, 3, 3)); // 3 days, well within the quota

        // Act
        ResponseEntity<?> result = controller.create(dto);

        // Assert
        assertTrue(result.getStatusCode().is2xxSuccessful());
        assertEquals(before + 1, leaveRequests.count());
    }

    // Regression test for the balance bug: an employee who has already used most
    // of the quota must NOT be able to create a request that pushes them over it.
    @Test
    void create_ExceedingQuota_ReturnsBadRequest() {
        // Arrange
        Employee emp = new Employee();
        emp.setName("Test Emp Over Quota");
        emp.setAnnualQuota(20);
        employees.save(emp);

        LeaveRequest approved = new LeaveRequest();
        approved.setEmployeeId(emp.getId());
        approved.setType(LeaveType.VACATION);
        approved.setStartDate(LocalDate.of(2026, 1, 1));
        approved.setEndDate(LocalDate.of(2026, 1, 18));
        approved.setDays(18);
        approved.setStatus(LeaveStatus.APPROVED);
        leaveRequests.save(approved);

        long before = leaveRequests.count();

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(emp.getId());
        dto.setType(LeaveType.VACATION);
        dto.setStartDate(LocalDate.of(2026, 3, 1));
        dto.setEndDate(LocalDate.of(2026, 3, 3)); // 3 days -> 18 + 3 = 21 > 20

        // Act
        ResponseEntity<?> result = controller.create(dto);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        assertEquals(before, leaveRequests.count());
    }

    @Test
    void create_ExactlyReachingQuota_Succeeds() {
        // Arrange
        Employee emp = new Employee();
        emp.setName("Test Emp At Quota");
        emp.setAnnualQuota(20);
        employees.save(emp);

        LeaveRequest approved = new LeaveRequest();
        approved.setEmployeeId(emp.getId());
        approved.setType(LeaveType.VACATION);
        approved.setStartDate(LocalDate.of(2026, 1, 1));
        approved.setEndDate(LocalDate.of(2026, 1, 18));
        approved.setDays(18);
        approved.setStatus(LeaveStatus.APPROVED);
        leaveRequests.save(approved);

        long before = leaveRequests.count();

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(emp.getId());
        dto.setType(LeaveType.VACATION);
        dto.setStartDate(LocalDate.of(2026, 3, 1));
        dto.setEndDate(LocalDate.of(2026, 3, 2)); // 2 days -> 18 + 2 = 20, exactly the quota

        // Act
        ResponseEntity<?> result = controller.create(dto);

        // Assert
        assertTrue(result.getStatusCode().is2xxSuccessful());
        assertEquals(before + 1, leaveRequests.count());
    }

    @Test
    void approve_PendingWithinQuota_ReturnsOkAndApproves() {
        // Arrange
        Employee emp = employeeWith18ApprovedDays("Test Emp Approve Ok");
        LeaveRequest pending = saveVacation(emp, LocalDate.of(2026, 3, 1), 2, LeaveStatus.PENDING);

        // Act
        ResponseEntity<?> result = controller.approve(pending.getId());

        // Assert
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(LeaveStatus.APPROVED, leaveRequests.findById(pending.getId()).orElseThrow().getStatus());
    }

    @Test
    void approve_AlreadyApproved_ReturnsConflict() {
        // Arrange
        Employee emp = employeeWith18ApprovedDays("Test Emp Already Approved");
        LeaveRequest approved = saveVacation(emp, LocalDate.of(2026, 3, 1), 2, LeaveStatus.APPROVED);

        // Act
        ResponseEntity<?> result = controller.approve(approved.getId());

        // Assert
        assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
    }

    @Test
    void approve_ExceedingQuota_ReturnsConflict() {
        // Arrange
        Employee emp = employeeWith18ApprovedDays("Test Emp Approve Over Quota");
        LeaveRequest pending = saveVacation(emp, LocalDate.of(2026, 3, 1), 3, LeaveStatus.PENDING); // 18 + 3 > 20

        // Act
        ResponseEntity<?> result = controller.approve(pending.getId());

        // Assert
        assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
        assertEquals(LeaveStatus.PENDING, leaveRequests.findById(pending.getId()).orElseThrow().getStatus());
    }

    @Test
    void approve_NonExistingId_ReturnsNotFound() {
        // Act
        ResponseEntity<?> result = controller.approve(-1L);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    // Two 2-day requests fit the remaining balance only one at a time (18 + 2 + 2 > 20).
    // Approving both at once must let exactly one through.
    @Test
    void approve_ConcurrentRequests_OnlyOneSucceeds() throws Exception {
        // Arrange
        Employee emp = employeeWith18ApprovedDays("Test Emp Concurrent");
        LeaveRequest first = saveVacation(emp, LocalDate.of(2026, 3, 1), 2, LeaveStatus.PENDING);
        LeaveRequest second = saveVacation(emp, LocalDate.of(2026, 4, 1), 2, LeaveStatus.PENDING);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        List<Future<ResponseEntity<?>>> futures;
        try {
            futures = List.of(first.getId(), second.getId()).stream()
                    .<Future<ResponseEntity<?>>>map(id -> pool.submit(() -> {
                        ready.countDown();
                        start.await();
                        return controller.approve(id);
                    }))
                    .toList();

            // Act
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            // Assert
            long ok = 0;
            long conflict = 0;
            for (Future<ResponseEntity<?>> f : futures) {
                HttpStatus status = HttpStatus.valueOf(f.get(30, TimeUnit.SECONDS).getStatusCode().value());
                if (status == HttpStatus.OK) ok++;
                if (status == HttpStatus.CONFLICT) conflict++;
            }
            assertEquals(1, ok);
            assertEquals(1, conflict);
        } finally {
            pool.shutdownNow();
        }

        int approvedDays = leaveRequests
                .findByEmployeeIdAndTypeAndStatus(emp.getId(), LeaveType.VACATION, LeaveStatus.APPROVED)
                .stream()
                .mapToInt(LeaveRequest::getDays)
                .sum();
        assertEquals(20, approvedDays);
    }

    // Employee with annualQuota 20 who already has an approved 18-day vacation.
    private Employee employeeWith18ApprovedDays(String name) {
        Employee emp = new Employee();
        emp.setName(name);
        emp.setAnnualQuota(20);
        employees.save(emp);

        saveVacation(emp, LocalDate.of(2026, 1, 1), 18, LeaveStatus.APPROVED);
        return emp;
    }

    private LeaveRequest saveVacation(Employee emp, LocalDate start, int days, LeaveStatus status) {
        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(emp.getId());
        request.setType(LeaveType.VACATION);
        request.setStartDate(start);
        request.setEndDate(start.plusDays(days - 1));
        request.setDays(days);
        request.setStatus(status);
        return leaveRequests.save(request);
    }
}
