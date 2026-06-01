package com.example.giga_test.task.repository;

import com.example.giga_test.model.Category;
import com.example.giga_test.model.Priority;
import com.example.giga_test.model.Status;
import com.example.giga_test.task.entity.TaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;



//интерфейс автоматически из DataJPA с множеством уже готовых функций
public interface TaskRepository extends JpaRepository<TaskEntity, Long>, JpaSpecificationExecutor<TaskEntity> {
    List<TaskEntity> findAllByStatus(Status status);
    //запрос без sql только благодаря названию
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

    }
