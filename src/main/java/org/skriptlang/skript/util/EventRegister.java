package org.skriptlang.skript.util;

import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * EventRegisters are generic containers for registering events.
 * They are used across Skript to provide additional API with consistent organization.
 * @param <E> The class representing the type of events this register will hold.
 */
public class EventRegister<E> {

	private final Set<E> events = new HashSet<>();

	/**
	 * Registers the provided event with this register.
	 * @param event The event to register.
	 */
	public void registerEvent(E event) {
		events.add(event);
	}

	/**
	 * Registers the provided event with.
	 * @param eventType The type of event being registered.
	 *  This is useful for registering an event that is a {@link FunctionalInterface} using a lambda.
	 * @param event The event to register.
	 */
	public <T extends E> void registerEvent(Class<T> eventType, T event) {
		events.add(event);
	}

	/**
	 * Unregisters the provided event.
	 * @param event The event to unregister.
	 */
	public void unregisterEvent(E event) {
		events.remove(event);
	}

	/**
	 * @return An unmodifiable set of this register's events.
	 */
	@Unmodifiable
	public Set<E> getEvents() {
		return Collections.unmodifiableSet(events);
	}

	/**
	 * @param type The type of events to get.
	 * @return An unmodifiable subset (of the specified type) of this register's events
	 */
	@Unmodifiable
	@SuppressWarnings("unchecked")
	public <T extends E> Set<T> getEvents(Class<T> type) {
		return Collections.unmodifiableSet(
			(Set<T>) events.stream()
				.filter(event -> type.isAssignableFrom(event.getClass()))
				.collect(Collectors.toSet())
		);
	}
	
}
