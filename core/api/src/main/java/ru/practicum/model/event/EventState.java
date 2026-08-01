package ru.practicum.model.event;

public enum EventState {
    PENDING, PUBLISHED, CANCELED;

    @Override
    public String toString() {
        return name();
    }
}