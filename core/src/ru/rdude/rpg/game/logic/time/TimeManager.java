package ru.rdude.rpg.game.logic.time;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashSet;
import java.util.Set;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class TimeManager implements TurnChangeObserver {

    @JsonIgnore
    private final Set<TimeObserver> timeObservers;
    private final Set<TimeChangeObserver> timeChangeObservers;

    private int minute = 0;
    private int hour = 12;
    private int day = 1;
    private int month = 1;
    private int year = 1;


    public TimeManager() {
        timeChangeObservers = new HashSet<>();
        timeObservers = new HashSet<>();
    }

    public String stringTime() {
        return String.format("%02d:%02d of day %d, month %d, year %d",
                minute, hour, day, month, year);
    }

    public int minute() { return minute; }
    public int hour() { return hour; }
    public int day() { return day; }
    public int month() { return month; }
    public int year() { return year; }

    private void calculateTime() {
        while (minute > 59) {
            minute -= 60;
            hour +=1;
        }
        while (minute < 0) {
            minute += 60;
            hour -= 1;
        }
        while (hour > 23) {
            hour -= 24;
            day += 1;
        }
        while (hour < 0) {
            hour += 24;
            day -= 1;
        }
        while (day > 28) {
            day -= 28;
            month += 1;
        }
        while (day < 0) {
            day += 28;
            month -= 1;
        }
        while (month > 12) {
            month -= 12;
            year += 1;
        }
        while (month < 0) {
            month += 12;
            year -= 1;
        }
    }

    public void increaseTime() {
        increaseTime(1);
    }

    public void increaseTime(int minutes) {
        this.minute += minutes;
        calculateTime();
        notifySubscribers(minutes);
    }

    public void setTime(int minute, int hour, int day, int month, int year) {
        this.minute = minute;
        this.hour = hour;
        this.day = day;
        this.month = month;
        this.year = year;
    }

    public void subscribe(TimeObserver observer) { timeObservers.add(observer); }
    public void subscribe(TimeChangeObserver observer) { timeChangeObservers.add(observer); }

    public void unsubscribe(TimeObserver observer) { timeObservers.remove(observer); }
    public void unsubscribe(TimeChangeObserver observer) { timeChangeObservers.remove(observer); }

    private void notifySubscribers(int minutesChanges) {
        timeObservers.forEach(obs -> obs.update(this));
        timeChangeObservers.forEach(obs -> obs.timeUpdate(minutesChanges));
    }

    @Override
    public void turnUpdate() {
        increaseTime();
    }
}
