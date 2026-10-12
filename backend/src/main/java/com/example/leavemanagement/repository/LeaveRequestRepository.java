package com.example.leavemanagement.repository;

import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findAllByOrderByStartDateDesc();

    List<LeaveRequest> findByEmployeeIdAndTypeAndStatus(Long employeeId, LeaveType type, LeaveStatus status);

    // Returns only the employeeId (not the whole request), so after the lock
    // findById reads the request fresh from the DB and not from JPA's cache
    @Query("SELECT r.employeeId FROM LeaveRequest r WHERE r.id = :id")
    Optional<Long> findEmployeeIdById(@Param("id") Long id);
}
