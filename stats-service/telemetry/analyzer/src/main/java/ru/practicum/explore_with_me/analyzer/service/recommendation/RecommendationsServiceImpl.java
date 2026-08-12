package ru.practicum.explore_with_me.analyzer.service.recommendation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import ru.practicum.explore_with_me.analyzer.model.repository.InteractionRepository;
import ru.practicum.explore_with_me.analyzer.model.repository.SimilarityRepository;
import ru.practicum.ewm.stats.proto.InteractionsCountRequestProto;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;
import ru.practicum.ewm.stats.proto.SimilarEventsRequestProto;
import ru.practicum.ewm.stats.proto.UserPredictionsRequestProto;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class RecommendationsServiceImpl implements RecommendationsService {

    private final InteractionRepository interactionRepository;
    private final SimilarityRepository similarityRepository;

    @Override
    public List<RecommendedEventProto> getInteractionsCount(final InteractionsCountRequestProto request) {
        return interactionRepository.getInteractionsSumByEventIds(request.getEventIdList()).stream()
                .map(rec -> RecommendedEventProto.newBuilder()
                        .setEventId(rec.eventId())
                        .setScore(rec.score())
                        .build())
                .toList();
    }

    @Override
    public List<RecommendedEventProto> getSimilarEvents(final SimilarEventsRequestProto request) {
        return similarityRepository.getSimilarEvents(request.getUserId(), request.getEventId(), Limit.of((int) request.getMaxResults())).stream()
                .map(rec -> RecommendedEventProto.newBuilder()
                        .setEventId(rec.eventId())
                        .setScore(rec.score())
                        .build())
                .toList();
    }

    @Override
    public List<RecommendedEventProto> getRecommendationsForUser(final UserPredictionsRequestProto request) {
        List<Long> candidateIds = similarityRepository.getSimilarEventIdsForUser(
                request.getUserId(),
                Limit.of((int) request.getMaxResults())
        );

        if (candidateIds.isEmpty()) {
            return List.of();
        }


        List<Object[]> firstPart = similarityRepository.calculateRatingsForUserFirst(request.getUserId(), candidateIds);
        List<Object[]> secondPart = similarityRepository.calculateRatingsForUserSecond(request.getUserId(), candidateIds);

        Map<Long, Double> ratingMap = Stream.concat(firstPart.stream(), secondPart.stream())
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Double) row[1],
                        Double::sum
                ));

        return candidateIds.stream()
                .map(eventId -> RecommendedEventProto.newBuilder()
                        .setEventId(eventId)
                        .setScore(ratingMap.getOrDefault(eventId, 0D))
                        .build())
                .collect(Collectors.toList());
    }
}