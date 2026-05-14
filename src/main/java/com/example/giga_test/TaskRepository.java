package com.example.giga_test;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

}

