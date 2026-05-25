package com.example.giga_test.task.repository;

import com.example.giga_test.model.Category;
import com.example.giga_test.model.Priority;
import com.example.giga_test.model.Status;
import com.example.giga_test.task.entity.TaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;



//интерфейс автоматически из DataJPA с множеством уже готовых функций
public interface TaskRepository extends JpaRepository<TaskEntity, Long> {
    List<TaskEntity> findAllByStatus(Status status);
    List<TaskEntity> findTop5ByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String title, String description);
    @Modifying
    @Query("""
            update TaskEntity t 
            set t.status =:status
            where t.id = :id
            """)
    void setStatus(
            @Param("id") Long id,
            @Param("status")Status status);

    @Query("""
    SELECT t from TaskEntity t
    WHERE (:assignedTo IS NULL OR t.assignedTo.id = :assignedTo)
      AND (:requester IS NULL OR t.requester.id = :requester)
      AND (:status IS NULL OR t.status = :status)
      AND (:priority IS NULL OR t.priority = :priority)
      AND (:category IS NULL OR t.category = :category)
      AND (:createdFrom IS NULL OR t.createdAt >= :createdFrom)
      AND (:createdTo IS NULL OR t.createdAt <= :createdTo)
      AND (:deadlineFrom IS NULL OR t.resolutionDeadline >= :deadlineFrom)
      AND (:deadlineTo IS NULL OR t.resolutionDeadline <= :deadlineTo)
            """)
    Page<TaskEntity> searchByFilter(
            @Param("assignedTo") Long assignedTo,
            @Param("requester") Long requester,
            @Param("status") Status status,
            @Param("priority") Priority priority,
            @Param("category") Category category,
            @Param("createdFrom") LocalDateTime createdFrom,
            @Param("createdTo") LocalDateTime createdTo,
            @Param("deadlineFrom") LocalDateTime deadlineFrom,
            @Param("deadlineTo") LocalDateTime deadlineTo,
            Pageable pageable
    );
}
