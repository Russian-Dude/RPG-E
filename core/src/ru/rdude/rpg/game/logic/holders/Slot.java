package ru.rdude.rpg.game.logic.holders;

import ru.rdude.rpg.game.logic.entities.Entity;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Slot<T extends Entity> {

    // need to drag and drop knows from which slot entity dragged
    private static Map<? super Entity, Slot<? extends Entity>> entitiesInSlots = new HashMap<>();

    private Set<SlotObserver> subscribers;

    protected T entity;

    protected Set<Predicate<T>> extraRequirements;

    protected Enum<?> marker;

    public Slot(Enum<?> marker, Predicate<T> ... extraRequirements) {
        subscribers = new HashSet<>();
        this.marker = marker;
        this.extraRequirements = Arrays.stream(extraRequirements).collect(Collectors.toSet());
    }

    public static <E extends Entity> Slot<? extends Entity> withEntity(E entity) {
        return entitiesInSlots.get(entity);
    }

    public T getEntity() {
        return entity;
    }

    public void setEntity(T item) {
        this.entity = item;
        entitiesInSlots.put(item, this);
        notifySubscribers(item);
    }

    public boolean hasEntity(T item) {
        return this.entity != null && this.entity.equals(item);
    }

    public boolean isEmpty() {
        return entity == null;
    }

    public boolean swapEntities(Slot<T> anotherSlot) {
        if (!isEntityMatchRequirements(anotherSlot.entity)) {
            return false;
        }
        T thisEntity = this.entity;
        setEntity(anotherSlot.entity);
        anotherSlot.setEntity(thisEntity);
        return true;
    }

    public boolean isEntityMatchRequirements(T t) {
        return extraRequirements.stream().allMatch(p -> p.test(t));
    }

    public Enum<?> getMarker() {
        return marker;
    }

    public void setMarker(Enum<?> marker) {
        this.marker = marker;
    }

    public void subscribe(SlotObserver subscriber) {
        this.subscribers.add(subscriber);
    }

    public void unsubscribe(SlotObserver subscriber) {
        this.subscribers.remove(subscriber);
    }

    private void notifySubscribers(T entity) {
        subscribers.forEach(subscriber -> subscriber.update(this, entity));
    }
}
