package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.TaskAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {
    List<TaskAssignment> findByTaskId(Long taskId);

    boolean existsByTaskIdAndUserId(Long taskId, Long userId);
}
