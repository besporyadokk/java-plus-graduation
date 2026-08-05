package ru.practicum.explore_with_me.event.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.explore_with_me.EndpointHitDto;
import ru.practicum.explore_with_me.StatResponseDto;
import ru.practicum.explore_with_me.StatsClient;
import ru.practicum.explore_with_me.event.dao.Event;
import ru.practicum.explore_with_me.event.dao.Location;
import ru.practicum.explore_with_me.event.mapper.EventMapper;
import ru.practicum.explore_with_me.event.repository.EventRepository;
import ru.practicum.explore_with_me.interaction_api.exception.BadRequestException;
import ru.practicum.explore_with_me.interaction_api.exception.ConflictException;
import ru.practicum.explore_with_me.interaction_api.exception.NotFoundException;
import ru.practicum.explore_with_me.interaction_api.model.category.client.CategoryServiceClient;
import ru.practicum.explore_with_me.interaction_api.model.category.dto.CategoryDto;
import ru.practicum.explore_with_me.interaction_api.model.event.EventState;
import ru.practicum.explore_with_me.interaction_api.model.event.dto.*;
import ru.practicum.explore_with_me.interaction_api.model.request.RequestStatus;
import ru.practicum.explore_with_me.interaction_api.model.request.client.ParticipationRequestServiceClient;
import ru.practicum.explore_with_me.interaction_api.model.request.dto.ParticipationRequestDto;
import ru.practicum.explore_with_me.interaction_api.model.user.client.UserServiceClient;
import ru.practicum.explore_with_me.interaction_api.model.user.dto.UserShortDto;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final UserServiceClient userServiceClient;
    private final CategoryServiceClient categoryServiceClient;
    private final ParticipationRequestServiceClient requestServiceClient;
    private final StatsClient statsClient;

    @Override
    public List<EventShortDto> getEventsPublic(PublicEventSearchParams params, HttpServletRequest request) {
        verifyRange(params.getRangeStart(), params.getRangeEnd());
        statsClient.hit(EndpointHitDto.builder()
                .app("ewm-service")
                .uri(request.getRequestURI())
                .ip(request.getRemoteAddr())
                .timestamp(LocalDateTime.now())
                .build());

        Pageable pageableWithSort = buildPageableWithSort(params.getPageable(), params.getSort());
        Specification<Event> spec = buildSpecification(
                params.getText(),
                params.getCategories(),
                params.getPaid(),
                params.getRangeStart(),
                params.getRangeEnd()
        );
        List<Event> events = eventRepository.findAll(spec, pageableWithSort).toList();

        if (events.isEmpty()) {
            return List.of();
        }

        Map<Long, CategoryDto> categoryMap = getCategoryMap(events);
        Map<Long, UserShortDto> userMap = getUserMap(events);
        Map<Long, Long> views = getEventsViews(events);
        Map<Long, List<ParticipationRequestDto>> confirmedRequests = getConfirmedRequests(events);

        if (params.getOnlyAvailable() != null && params.getOnlyAvailable()) {
            events = events.stream()
                    .filter(event -> {
                        int limit = event.getParticipantLimit() != null ? event.getParticipantLimit() : 0;
                        int confirmed = confirmedRequests.getOrDefault(event.getId(), List.of()).size();
                        return confirmed < limit;
                    })
                    .collect(Collectors.toList());
        }

        return events.stream()
                .map(event -> {
                    EventShortDto dto = eventMapper.toEventShortDto(
                            event,
                            categoryMap.get(event.getCategoryId()),
                            userMap.get(event.getInitiatorId())
                    );
                    dto.setViews(views.getOrDefault(dto.getId(), 0L));
                    dto.setConfirmedRequests(confirmedRequests.getOrDefault(dto.getId(), List.of()).size());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Set<EventShortDto> getEventShortDtoSetByIds(Set<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Set.of();
        }
        List<Event> events = eventRepository.findAllById(eventIds);
        if (events.isEmpty()) {
            return Set.of();
        }

        Map<Long, CategoryDto> categoryMap = getCategoryMap(events);
        Map<Long, UserShortDto> userMap = getUserMap(events);
        Map<Long, List<ParticipationRequestDto>> confirmedRequests = getConfirmedRequests(events);

        return events.stream()
                .map(event -> {
                    EventShortDto dto = eventMapper.toEventShortDto(
                            event,
                            categoryMap.get(event.getCategoryId()),
                            userMap.get(event.getInitiatorId())
                    );
                    dto.setConfirmedRequests(confirmedRequests.getOrDefault(dto.getId(), List.of()).size());
                    return dto;
                })
                .collect(Collectors.toSet());
    }

    @Override
    public List<EventShortDto> getEventsByUser(Long userId, Pageable pageable) {
        List<Event> events = eventRepository.findByInitiatorId(userId, pageable).getContent();
        if (events.isEmpty()) {
            return List.of();
        }

        Map<Long, CategoryDto> categoryMap = getCategoryMap(events);
        Map<Long, UserShortDto> userMap = getUserMap(events);
        Map<Long, List<ParticipationRequestDto>> confirmedRequests = getConfirmedRequests(events);

        return events.stream()
                .map(event -> {
                    EventShortDto dto = eventMapper.toEventShortDto(
                            event,
                            categoryMap.get(event.getCategoryId()),
                            userMap.get(event.getInitiatorId())
                    );
                    dto.setConfirmedRequests(confirmedRequests.getOrDefault(dto.getId(), List.of()).size());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public EventShortDto getEventShortDtoByIdClient(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event with id " + id + " not found"));
        CategoryDto categoryDto = categoryServiceClient.getCategoryById(event.getCategoryId());
        UserShortDto userShortDto = userServiceClient.getUserShortDtoClientById(event.getInitiatorId());
        EventShortDto dto = eventMapper.toEventShortDto(event, categoryDto, userShortDto);
        List<ParticipationRequestDto> confirmed = requestServiceClient.getConfirmedRequestsCount(
                List.of(id), RequestStatus.CONFIRMED
        ).getOrDefault(id, List.of());
        dto.setConfirmedRequests(confirmed.size());
        return dto;
    }

    @Override
    public EventFullDto getEventFullDtoByIdClient(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event with id " + id + " not found"));
        CategoryDto categoryDto = categoryServiceClient.getCategoryById(event.getCategoryId());
        UserShortDto userShortDto = userServiceClient.getUserShortDtoClientById(event.getInitiatorId());
        EventFullDto dto = eventMapper.toEventFullDto(event, categoryDto, userShortDto);
        List<ParticipationRequestDto> confirmed = requestServiceClient.getConfirmedRequestsCount(
                List.of(id), RequestStatus.CONFIRMED
        ).getOrDefault(id, List.of());
        dto.setConfirmedRequests(confirmed.size());
        return dto;
    }

    @Override
    @Transactional
    public EventFullDto getEventById(Long id, HttpServletRequest request) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event not found");
        }
        statsClient.hit(EndpointHitDto.builder()
                .app("ewm-service")
                .uri(request.getRequestURI())
                .ip(request.getRemoteAddr())
                .timestamp(LocalDateTime.now())
                .build());

        CategoryDto categoryDto = categoryServiceClient.getCategoryById(event.getCategoryId());
        UserShortDto userShortDto = userServiceClient.getUserShortDtoClientById(event.getInitiatorId());
        EventFullDto dto = eventMapper.toEventFullDto(event, categoryDto, userShortDto);

        Map<Long, Long> views = getEventsViews(List.of(event));
        dto.setViews(views.getOrDefault(id, 0L));

        List<ParticipationRequestDto> confirmed = requestServiceClient.getConfirmedRequestsCount(
                List.of(id), RequestStatus.CONFIRMED
        ).getOrDefault(id, List.of());
        dto.setConfirmedRequests(confirmed.size());

        return dto;
    }

    @Override
    public EventFullDto getEventByUser(Long userId, Long eventId) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        CategoryDto categoryDto = categoryServiceClient.getCategoryById(event.getCategoryId());
        UserShortDto userShortDto = userServiceClient.getUserShortDtoClientById(userId);
        EventFullDto dto = eventMapper.toEventFullDto(event, categoryDto, userShortDto);

        List<ParticipationRequestDto> confirmed = requestServiceClient.getConfirmedRequestsCount(
                List.of(eventId), RequestStatus.CONFIRMED
        ).getOrDefault(eventId, List.of());
        dto.setConfirmedRequests(confirmed.size());

        return dto;
    }

    @Override
    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto newEventDto) {
        UserShortDto userShortDto = userServiceClient.getUserShortDtoClientById(userId);
        CategoryDto categoryDto = categoryServiceClient.getCategoryById(newEventDto.getCategory());

        if (newEventDto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new BadRequestException("До даты мероприятия должно быть не менее 2 часов");
        }

        Event event = eventMapper.toEvent(newEventDto);
        event.setInitiatorId(userId);
        event.setCategoryId(categoryDto.getId());
        event.setCreatedOn(LocalDateTime.now());
        event.setState(EventState.PENDING);

        if (newEventDto.getLocation() != null) {
            event.setLocation(eventMapper.toLocation(newEventDto.getLocation()));
        }

        Event savedEvent = eventRepository.save(event);
        EventFullDto dto = eventMapper.toEventFullDto(savedEvent, categoryDto, userShortDto);
        dto.setConfirmedRequests(0);
        return dto;
    }

    @Override
    @Transactional
    public EventFullDto updateEventByUser(Long userId, Long eventId, UpdateEventUserRequest updateRequest) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        CategoryDto categoryDto = categoryServiceClient.getCategoryById(event.getCategoryId());
        UserShortDto userShortDto = userServiceClient.getUserShortDtoClientById(userId);

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Cannot update published event");
        }
        if (updateRequest.getEventDate() != null &&
                updateRequest.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new BadRequestException("Event date must be at least 2 hours from now");
        }

        if (updateRequest.getStateAction() != null) {
            if (updateRequest.getStateAction().equals("SEND_TO_REVIEW")) {
                event.setState(EventState.PENDING);
            } else if (updateRequest.getStateAction().equals("CANCEL_REVIEW")) {
                event.setState(EventState.CANCELED);
            }
        }

        updateEventFields(event, updateRequest);
        Event updatedEvent = eventRepository.save(event);

        EventFullDto dto = eventMapper.toEventFullDto(updatedEvent, categoryDto, userShortDto);
        List<ParticipationRequestDto> confirmed = requestServiceClient.getConfirmedRequestsCount(
                List.of(eventId), RequestStatus.CONFIRMED
        ).getOrDefault(eventId, List.of());
        dto.setConfirmedRequests(confirmed.size());

        return dto;
    }

    @Override
    public List<EventFullDto> getEventsForAdmin(AdminEventSearchParams params) {
        Specification<Event> spec = buildAdminSpecification(
                params.getUsers(),
                params.getStates(),
                params.getCategories(),
                params.getRangeStart(),
                params.getRangeEnd()
        );
        List<Event> events = eventRepository.findAll(spec, params.getPageable()).toList();
        if (events.isEmpty()) {
            return List.of();
        }

        Map<Long, CategoryDto> categoryMap = getCategoryMap(events);
        Map<Long, UserShortDto> userMap = getUserMap(events);
        Map<Long, Long> views = getEventsViews(events);
        Map<Long, List<ParticipationRequestDto>> confirmedRequests = getConfirmedRequests(events);

        return events.stream()
                .map(event -> {
                    EventFullDto dto = eventMapper.toEventFullDto(
                            event,
                            categoryMap.get(event.getCategoryId()),
                            userMap.get(event.getInitiatorId())
                    );
                    dto.setViews(views.getOrDefault(dto.getId(), 0L));
                    dto.setConfirmedRequests(confirmedRequests.getOrDefault(dto.getId(), List.of()).size());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest updateRequest) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        CategoryDto categoryDto = categoryServiceClient.getCategoryById(event.getCategoryId());
        UserShortDto userShortDto = userServiceClient.getUserShortDtoClientById(event.getInitiatorId());

        if (updateRequest.getStateAction() != null) {
            if (updateRequest.getStateAction().equals("PUBLISH_EVENT")) {
                if (event.getState() != EventState.PENDING) {
                    throw new ConflictException("Cannot publish event that is not in PENDING state");
                }
                if (event.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                    throw new ConflictException("Event date must be at least 1 hour from publication");
                }
                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
            } else if (updateRequest.getStateAction().equals("REJECT_EVENT")) {
                if (event.getState() == EventState.PUBLISHED) {
                    throw new ConflictException("Cannot reject published event");
                }
                event.setState(EventState.CANCELED);
            }
        }

        updateEventFields(event, updateRequest);
        Event updatedEvent = eventRepository.save(event);

        EventFullDto dto = eventMapper.toEventFullDto(updatedEvent, categoryDto, userShortDto);
        List<ParticipationRequestDto> confirmed = requestServiceClient.getConfirmedRequestsCount(
                List.of(eventId), RequestStatus.CONFIRMED
        ).getOrDefault(eventId, List.of());
        dto.setConfirmedRequests(confirmed.size());

        return dto;
    }

    private void verifyRange(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new BadRequestException("Дата начала не может быть позже даты окончания");
        }
    }

    private Pageable buildPageableWithSort(Pageable pageable, String sort) {
        if (sort == null || sort.isEmpty()) {
            return pageable;
        }
        Sort sortObj;
        if ("EVENT_DATE".equalsIgnoreCase(sort)) {
            sortObj = Sort.by("eventDate").ascending();
        } else {
            sortObj = Sort.unsorted();
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortObj);
    }

    private Map<Long, CategoryDto> getCategoryMap(List<Event> events) {
        List<Long> categoryIds = events.stream().map(Event::getCategoryId).distinct().collect(Collectors.toList());
        return categoryServiceClient.getCategoriesByIds(categoryIds).stream()
                .collect(Collectors.toMap(CategoryDto::getId, Function.identity()));
    }

    private Map<Long, UserShortDto> getUserMap(List<Event> events) {
        List<Long> initiatorIds = events.stream().map(Event::getInitiatorId).distinct().collect(Collectors.toList());
        return userServiceClient.getUsersShortByIds(initiatorIds).stream()
                .collect(Collectors.toMap(UserShortDto::getId, Function.identity()));
    }

    private Map<Long, List<ParticipationRequestDto>> getConfirmedRequests(List<Event> events) {
        List<Long> eventIds = events.stream().map(Event::getId).collect(Collectors.toList());
        return requestServiceClient.getConfirmedRequestsCount(eventIds, RequestStatus.CONFIRMED);
    }

    private Map<Long, Long> getEventsViews(List<Event> events) {
        List<String> uris = events.stream()
                .map(event -> String.format("/events/%s", event.getId()))
                .collect(Collectors.toList());
        LocalDateTime startDate = events.stream()
                .map(Event::getPublishedOn)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().minusYears(1));
        Map<Long, Long> viewStats = new HashMap<>();
        try {
            List<StatResponseDto> stats = statsClient.getStats(startDate, LocalDateTime.now(), uris, true);
            viewStats = stats.stream()
                    .filter(s -> s.getUri().startsWith("/events/"))
                    .collect(Collectors.toMap(
                            s -> Long.parseLong(s.getUri().substring("/events/".length())),
                            StatResponseDto::getHits
                    ));
        } catch (Exception e) {
            log.error("Error getting stats: {}", e.getMessage());
        }
        return viewStats;
    }

    private Specification<Event> searchText(String text) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("annotation")), "%" + text + "%"),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), "%" + text + "%")
                );
    }

    private Specification<Event> searchCategoryIn(List<Long> categories) {
        return (root, query, criteriaBuilder) -> root.get("categoryId").in(categories);
    }

    private Specification<Event> searchPaid(Boolean paid) {
        if (paid == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("paid"), paid);
    }

    private Specification<Event> searchAfterDate(LocalDateTime rangeStart) {
        LocalDateTime start = Objects.requireNonNullElse(rangeStart, LocalDateTime.now());
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThan(root.get("eventDate"), start);
    }

    private Specification<Event> searchBeforeDate(LocalDateTime rangeEnd) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThan(root.get("eventDate"), rangeEnd);
    }

    private Specification<Event> searchPublished() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("state"), EventState.PUBLISHED);
    }

    private Specification<Event> buildSpecification(String text, List<Long> categories, Boolean paid,
                                                    LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        Specification<Event> spec = Specification.where(null);
        if (text != null && !text.isBlank()) {
            spec = spec.and(searchText(text.toLowerCase()));
        }
        if (categories != null && !categories.isEmpty()) {
            spec = spec.and(searchCategoryIn(categories));
        }
        if (paid != null) {
            spec = spec.and(searchPaid(paid));
        }
        spec = spec.and(searchAfterDate(rangeStart));
        if (rangeEnd != null) {
            spec = spec.and(searchBeforeDate(rangeEnd));
        }
        return spec.and(searchPublished());
    }

    private Specification<Event> buildAdminSpecification(List<Long> users, List<String> states,
                                                         List<Long> categories, LocalDateTime rangeStart,
                                                         LocalDateTime rangeEnd) {
        Specification<Event> spec = Specification.where(null);
        if (users != null && !users.isEmpty()) {
            spec = spec.and((root, query, criteriaBuilder) -> root.get("initiatorId").in(users));
        }
        if (states != null && !states.isEmpty()) {
            spec = spec.and((root, query, criteriaBuilder) -> root.get("state").as(String.class).in(states));
        }
        if (categories != null && !categories.isEmpty()) {
            spec = spec.and((root, query, criteriaBuilder) -> root.get("categoryId").in(categories));
        }
        if (rangeStart != null) {
            spec = spec.and((root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("eventDate"), rangeStart));
        }
        if (rangeEnd != null) {
            spec = spec.and((root, query, criteriaBuilder) -> criteriaBuilder.lessThanOrEqualTo(root.get("eventDate"), rangeEnd));
        }
        return spec;
    }

    private void updateEventFields(Event event, UpdateEventUserRequest updateRequest) {
        if (updateRequest.getAnnotation() != null) {
            event.setAnnotation(updateRequest.getAnnotation());
        }
        if (updateRequest.getCategory() != null) {
            event.setCategoryId(updateRequest.getCategory());
        }
        if (updateRequest.getDescription() != null) {
            event.setDescription(updateRequest.getDescription());
        }
        if (updateRequest.getEventDate() != null) {
            event.setEventDate(updateRequest.getEventDate());
        }
        if (updateRequest.getLocation() != null) {
            event.setLocation(eventMapper.toLocation(updateRequest.getLocation()));
        }
        if (updateRequest.getPaid() != null) {
            event.setPaid(updateRequest.getPaid());
        }
        if (updateRequest.getParticipantLimit() != null) {
            event.setParticipantLimit(updateRequest.getParticipantLimit());
        }
        if (updateRequest.getRequestModeration() != null) {
            event.setRequestModeration(updateRequest.getRequestModeration());
        }
        if (updateRequest.getTitle() != null) {
            event.setTitle(updateRequest.getTitle());
        }
    }

    private void updateEventFields(Event event, UpdateEventAdminRequest updateRequest) {
        if (updateRequest.getAnnotation() != null) {
            event.setAnnotation(updateRequest.getAnnotation());
        }
        if (updateRequest.getCategory() != null) {
            CategoryDto category = categoryServiceClient.getCategoryById(updateRequest.getCategory());
            event.setCategoryId(category.getId());
        }
        if (updateRequest.getDescription() != null) {
            event.setDescription(updateRequest.getDescription());
        }
        if (updateRequest.getEventDate() != null) {
            event.setEventDate(updateRequest.getEventDate());
        }
        if (updateRequest.getLocation() != null) {
            event.setLocation(eventMapper.toLocation(updateRequest.getLocation()));
        }
        if (updateRequest.getPaid() != null) {
            event.setPaid(updateRequest.getPaid());
        }
        if (updateRequest.getParticipantLimit() != null) {
            event.setParticipantLimit(updateRequest.getParticipantLimit());
        }
        if (updateRequest.getRequestModeration() != null) {
            event.setRequestModeration(updateRequest.getRequestModeration());
        }
        if (updateRequest.getTitle() != null) {
            event.setTitle(updateRequest.getTitle());
        }
    }

    @Override
    public void validateCategoryHasNoEvents(Long categoryId) {
        if (eventRepository.existsByCategoryId(categoryId)) {
            throw new ConflictException("Category has associated events and cannot be deleted");
        }
    }

    @Override
    public void validateEventExistingById(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new NotFoundException("Ивента с id=" + eventId + " нет в БД!");
        }
    }
}