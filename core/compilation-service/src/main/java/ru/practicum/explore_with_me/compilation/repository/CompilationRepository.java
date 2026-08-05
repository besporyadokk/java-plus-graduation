package ru.practicum.explore_with_me.compilation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.explore_with_me.compilation.dao.Compilation;

import java.util.List;

public interface CompilationRepository extends JpaRepository<Compilation, Long> {
    Page<Compilation> findByPinned(Boolean pinned, Pageable pageable);

    @Query("SELECT c.id, e FROM Compilation c JOIN c.eventsId e WHERE c.id IN :ids")
    List<Object[]> findAllEventsIdByCompilationIds(@Param("ids") List<Long> ids);
}