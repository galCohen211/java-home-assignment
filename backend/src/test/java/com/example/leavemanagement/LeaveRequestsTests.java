package com.example.leavemanagement;

import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.exception.ConflictException;
import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import com.example.leavemanagement.service.LeaveRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs against a real, throwaway PostgreSQL started by Testcontainers.
// (Docker must be available on the machine running the tests.)
// HTTP tests go through MockMvc so @Valid and GlobalExceptionHandler are exercised.
@SpringBootTest
@AutoConfigureMockMvc
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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LeaveRequestService service;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private LeaveRequestRepository leaveRequests;

    @Test
    void create_WithinQuota_Succeeds() throws Exception {
        // Arrange
        Employee emp = new Employee();
        emp.setName("Test Emp");
        emp.setAnnualQuota(20);
        employees.save(emp);

        long before = leaveRequests.count();

        CreateLeaveRequestDto dto = vacationDto(emp.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3)); // 3 days, well within the quota

        // Act
        ResultActions result = postCreate(dto);

        // Assert
        result.andExpect(status().isOk());
        assertEquals(before + 1, leaveRequests.count());
    }

    // Regression test for the balance bug: an employee who has already used most
    // of the quota must NOT be able to create a request that pushes them over it.
    @Test
    void create_ExceedingQuota_ReturnsBadRequest() throws Exception {
        // Arrange
        Employee emp = employeeWith18ApprovedDays("Test Emp Over Quota");

        long before = leaveRequests.count();

        CreateLeaveRequestDto dto = vacationDto(emp.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3)); // 3 days -> 18 + 3 = 21 > 20

        // Act
        ResultActions result = postCreate(dto);

        // Assert
        result.andExpect(status().isBadRequest())
              .andExpect(content().string("Not enough vacation balance"));
        assertEquals(before, leaveRequests.count());
    }

    @Test
    void create_ExactlyReachingQuota_Succeeds() throws Exception {
        // Arrange
        Employee emp = employeeWith18ApprovedDays("Test Emp At Quota");

        long before = leaveRequests.count();

        CreateLeaveRequestDto dto = vacationDto(emp.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2)); // 2 days -> 18 + 2 = 20, exactly the quota

        // Act
        ResultActions result = postCreate(dto);

        // Assert
        result.andExpect(status().isOk());
        assertEquals(before + 1, leaveRequests.count());
    }

    @Test
    void create_UnknownEmployee_ReturnsNotFound() throws Exception {
        // Arrange
        long before = leaveRequests.count();
        CreateLeaveRequestDto dto = vacationDto(-1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2));

        // Act
        ResultActions result = postCreate(dto);

        // Assert
        result.andExpect(status().isNotFound())
              .andExpect(content().string("Employee not found"));
        assertEquals(before, leaveRequests.count());
    }

    @Test
    void create_MissingFields_ReturnsBadRequest() throws Exception {
        // Arrange
        long before = leaveRequests.count();

        // Act
        ResultActions result = postCreate(new CreateLeaveRequestDto());

        // Assert
        result.andExpect(status().isBadRequest())
              .andExpect(content().string(
                      "employeeId is required; endDate is required; startDate is required; type is required"));
        assertEquals(before, leaveRequests.count());
    }

    @Test
    void create_StartAfterEnd_ReturnsBadRequest() throws Exception {
        // Arrange
        Employee emp = new Employee();
        emp.setName("Test Emp Bad Range");
        emp.setAnnualQuota(20);
        employees.save(emp);

        long before = leaveRequests.count();

        CreateLeaveRequestDto dto = vacationDto(emp.getId(), LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 1));

        // Act
        ResultActions result = postCreate(dto);

        // Assert
        result.andExpect(status().isBadRequest())
              .andExpect(content().string("startDate must be on or before endDate"));
        assertEquals(before, leaveRequests.count());
    }

    @Test
    void approve_PendingWithinQuota_ReturnsOkAndApproves() throws Exception {
        // Arrange
        Employee emp = employeeWith18ApprovedDays("Test Emp Approve Ok");
        LeaveRequest pending = saveVacation(emp, LocalDate.of(2026, 3, 1), 2, LeaveStatus.PENDING);

        // Act
        ResultActions result = postApprove(pending.getId());

        // Assert
        result.andExpect(status().isOk());
        assertEquals(LeaveStatus.APPROVED, leaveRequests.findById(pending.getId()).orElseThrow().getStatus());
    }

    @Test
    void approve_AlreadyApproved_ReturnsConflict() throws Exception {
        // Arrange
        Employee emp = employeeWith18ApprovedDays("Test Emp Already Approved");
        LeaveRequest approved = saveVacation(emp, LocalDate.of(2026, 3, 1), 2, LeaveStatus.APPROVED);

        // Act
        ResultActions result = postApprove(approved.getId());

        // Assert
        result.andExpect(status().isConflict())
              .andExpect(content().string("Only pending requests can be approved"));
    }

    @Test
    void approve_ExceedingQuota_ReturnsConflict() throws Exception {
        // Arrange
        Employee emp = employeeWith18ApprovedDays("Test Emp Approve Over Quota");
        LeaveRequest pending = saveVacation(emp, LocalDate.of(2026, 3, 1), 3, LeaveStatus.PENDING); // 18 + 3 > 20

        // Act
        ResultActions result = postApprove(pending.getId());

        // Assert
        result.andExpect(status().isConflict())
              .andExpect(content().string("Not enough vacation balance"));
        assertEquals(LeaveStatus.PENDING, leaveRequests.findById(pending.getId()).orElseThrow().getStatus());
    }

    @Test
    void approve_NonExistingId_ReturnsNotFound() throws Exception {
        // Act
        ResultActions result = postApprove(-1L);

        // Assert
        result.andExpect(status().isNotFound())
              .andExpect(content().string("Leave request not found"));
    }

    // Two 2-day requests fit the remaining balance only one at a time (18 + 2 + 2 > 20).
    // Approving both at once must let exactly one through. Calls the service directly,
    // since the locking lives there; HTTP mapping of ConflictException is covered above.
    @Test
    void approve_ConcurrentRequests_OnlyOneSucceeds() throws Exception {
        // Arrange
        Employee emp = employeeWith18ApprovedDays("Test Emp Concurrent");
        LeaveRequest first = saveVacation(emp, LocalDate.of(2026, 3, 1), 2, LeaveStatus.PENDING);
        LeaveRequest second = saveVacation(emp, LocalDate.of(2026, 4, 1), 2, LeaveStatus.PENDING);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            // Each task returns true if its approval succeeded, false on a conflict.
            List<Future<Boolean>> futures = List.of(first.getId(), second.getId()).stream()
                    .map(id -> pool.submit(() -> {
                        ready.countDown();
                        start.await();
                        try {
                            service.approve(id);
                            return true;
                        } catch (ConflictException e) {
                            return false;
                        }
                    }))
                    .toList();

            // Act
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            // Assert
            long succeeded = 0;
            for (Future<Boolean> f : futures) {
                if (f.get(30, TimeUnit.SECONDS)) succeeded++;
            }
            assertEquals(1, succeeded);
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

    @Test
    void search_PartialName_ReturnsMatchingRequests() throws Exception {
        // Arrange
        Employee emp = new Employee();
        emp.setName("Searchable Zorkowski");
        emp.setAnnualQuota(20);
        employees.save(emp);
        LeaveRequest request = saveVacation(emp, LocalDate.of(2026, 3, 1), 2, LeaveStatus.PENDING);

        // Act
        ResultActions result = mockMvc.perform(get("/api/leave-requests/search").param("name", "Zork"));

        // Assert
        result.andExpect(status().isOk())
              .andExpect(jsonPath("$.length()").value(1))
              .andExpect(jsonPath("$[0].id").value(request.getId()));
    }

    // Regression test for SQL injection: with the old concatenated query this
    // payload turned the WHERE clause into "always true" and returned every request.
    @Test
    void search_SqlInjectionPayload_ReturnsNoResults() throws Exception {
        // Arrange: make sure there is data that a successful injection would expose.
        employeeWith18ApprovedDays("Test Emp Injection");

        // Act
        ResultActions result = mockMvc.perform(get("/api/leave-requests/search").param("name", "' OR '1'='1"));

        // Assert
        result.andExpect(status().isOk())
              .andExpect(jsonPath("$.length()").value(0));
    }

    private ResultActions postCreate(CreateLeaveRequestDto dto) throws Exception {
        return mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)));
    }

    private ResultActions postApprove(Long id) throws Exception {
        return mockMvc.perform(post("/api/leave-requests/{id}/approve", id));
    }

    private CreateLeaveRequestDto vacationDto(Long employeeId, LocalDate start, LocalDate end) {
        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(employeeId);
        dto.setType(LeaveType.VACATION);
        dto.setStartDate(start);
        dto.setEndDate(end);
        return dto;
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
