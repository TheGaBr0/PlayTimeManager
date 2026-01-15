package me.thegabro.playtimemanager.Customizations.PlaytimeFormats;
import java.io.File;

public class PlaytimeFormat {

    private String name;
    private final String yearsSingular;
    private final String yearsPlural;
    private final String monthsSingular;
    private final String monthsPlural;
    private final String weeksSingular;
    private final String weeksPlural;
    private final String daysSingular;
    private final String daysPlural;
    private final String hoursSingular;
    private final String hoursPlural;
    private final String minutesSingular;
    private final String minutesPlural;
    private final String secondsSingular;
    private final String secondsPlural;
    private final String formatting;
    private final File formatFile;
    private final boolean distributeRemovedTime;

    public PlaytimeFormat(File formatFile, String name, String yearsSingular, String yearsPlural,
                          String monthsSingular, String monthsPlural,
                          String weeksSingular, String weeksPlural, String daysSingular,
                          String daysPlural, String hoursSingular, String hoursPlural,
                          String minutesSingular, String minutesPlural, String secondsSingular,
                          String secondsPlural, String formatting, boolean distributeRemovedTime) {
        this.formatFile = formatFile;
        this.name = name;
        this.yearsSingular = yearsSingular;
        this.yearsPlural = yearsPlural;
        this.monthsSingular = monthsSingular;
        this.monthsPlural = monthsPlural;
        this.weeksSingular = weeksSingular;
        this.weeksPlural = weeksPlural;
        this.daysSingular = daysSingular;
        this.daysPlural = daysPlural;
        this.hoursSingular = hoursSingular;
        this.hoursPlural = hoursPlural;
        this.minutesSingular = minutesSingular;
        this.minutesPlural = minutesPlural;
        this.secondsSingular = secondsSingular;
        this.secondsPlural = secondsPlural;
        this.formatting = formatting;
        this.distributeRemovedTime = distributeRemovedTime;
    }

    // Getters
    public String getName() {
        return name;
    }

    public String getYearsSingular() {
        return yearsSingular;
    }

    public String getYearsPlural() {
        return yearsPlural;
    }

    public String getMonthsSingular() { return monthsSingular; }

    public String getMonthsPlural() { return monthsPlural; }

    public String getWeeksSingular() { return weeksSingular; }

    public String getWeeksPlural() { return weeksPlural; }

    public String getDaysSingular() {
        return daysSingular;
    }

    public String getDaysPlural() {
        return daysPlural;
    }

    public String getHoursSingular() {
        return hoursSingular;
    }

    public String getHoursPlural() {
        return hoursPlural;
    }

    public String getMinutesSingular() {
        return minutesSingular;
    }

    public String getMinutesPlural() {
        return minutesPlural;
    }

    public String getSecondsSingular() {
        return secondsSingular;
    }

    public String getSecondsPlural() {
        return secondsPlural;
    }

    public String getFormatting() {
        return formatting;
    }

    public boolean shouldDistributeRemovedTime(){ return distributeRemovedTime; }

    // Utility methods
    public String getYearsLabel(int years) {
        return years == 1 ? yearsSingular : yearsPlural;
    }

    public String getMonthsLabel(int months) {
        return months == 1 ? monthsSingular : monthsPlural;
    }

    public String getWeeksLabel(int weeks) { return weeks == 1 ? weeksSingular : weeksPlural; }

    public String getDaysLabel(int days) { return days == 1 ? daysSingular : daysPlural; }

    public String getHoursLabel(int hours) {
        return hours == 1 ? hoursSingular : hoursPlural;
    }

    public String getMinutesLabel(int minutes) {
        return minutes == 1 ? minutesSingular : minutesPlural;
    }

    public String getSecondsLabel(int seconds) {
        return seconds == 1 ? secondsSingular : secondsPlural;
    }

    public File getFormatFile(){ return formatFile; }

    @Override
    public String toString() {
        return "PlaytimeFormat{" +
                "name='" + name + '\'' +
                ", formatting='" + formatting + '\'' +
                '}';
    }
}