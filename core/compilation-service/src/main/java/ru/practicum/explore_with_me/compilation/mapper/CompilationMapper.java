package ru.practicum.explore_with_me.compilation.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.practicum.explore_with_me.compilation.dao.Compilation;
import ru.practicum.model.compilation.dto.CompilationDto;
import ru.practicum.model.compilation.dto.NewCompilationDto;
import ru.practicum.model.event.dto.EventShortDto;

import java.util.Set;

@Mapper(componentModel = "spring")
public interface CompilationMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "eventsId", source = "events")
    Compilation toCompilation(NewCompilationDto dto);

    @Mapping(target = "events", ignore = true)
    CompilationDto toCompilationDtoTemp(Compilation compilation);

    default CompilationDto toCompilationDto(Compilation compilation, Set<EventShortDto> events) {
        CompilationDto dto = toCompilationDtoTemp(compilation);
        dto.setEvents(events);
        return dto;
    }
}
