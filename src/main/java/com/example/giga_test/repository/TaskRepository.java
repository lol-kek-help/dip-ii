package com.example.giga_test.repository;

import com.example.giga_test.model.Status;
import com.example.giga_test.entity.TaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;


//интерфейс автоматически из DataJPA с множеством уже готовых функций
public interface TaskRepository extends JpaRepository<TaskEntity, Long> {
    List<TaskEntity> findAllByStatus(Status status);
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
            """)
    Page<TaskEntity> searchByFilter(
            @Param("assignedTo") Long assignedTo,
            @Param ("requester") Long requester,
            Pageable pageable
    );
}

