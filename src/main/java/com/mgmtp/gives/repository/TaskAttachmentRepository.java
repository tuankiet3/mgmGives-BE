package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.TaskAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {
    Optional<TaskAttachment> findByIdAndTaskId(Long id, Long taskId);
}
