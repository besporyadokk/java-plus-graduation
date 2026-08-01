package ru.practicum.model.request;

public enum RequestStatus {
    PENDING, CONFIRMED, REJECTED, CANCELED;

    @Override
    public String toString() {
        return name();
    }
}