package com.example.giga_test;

import org.springframework.data.jpa.repository.JpaRepository;
//интерфейс автоматически из DataJPA с множеством уже готовых функций
public interface TaskRepository extends JpaRepository<TaskEntity, Long> {

}

