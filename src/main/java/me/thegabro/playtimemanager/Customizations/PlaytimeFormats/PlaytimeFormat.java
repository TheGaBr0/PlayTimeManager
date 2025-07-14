package me.thegabro.playtimemanager.Customizations.PlaytimeFormats;

public class PlaytimeFormat {

    private String name;
    private String yearsSingular;
    private String yearsPlural;
    private String daysSingular;
    private String daysPlural;
    private String hoursSingular;
    private String hoursPlural;
    private String minutesSingular;
    private String minutesPlural;
    private String secondsSingular;
    private String secondsPlural;
    private String formatting;

    public PlaytimeFormat(String name, String yearsSingular, String yearsPlural, String daysSingular,
                          String daysPlural, String hoursSingular, String hoursPlural,
                          String minutesSingular, String minutesPlural, String secondsSingular,
                          String secondsPlural, String formatting) {
        this.name = name;
        this.yearsSingular = yearsSingular;
        this.yearsPlural = yearsPlural;
        this.daysSingular = daysSingular;
        this.daysPlural = daysPlural;
        this.hoursSingular = hoursSingular;
        this.hoursPlural = hoursPlural;
        this.minutesSingular = minutesSingular;
        this.minutesPlural = minutesPlural;
        this.secondsSingular = secondsSingular;
        this.secondsPlural = secondsPlural;
        this.formatting = formatting;
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

    // Setters
    public void setName(String name) {
        this.name = name;
    }

    public void setYearsSingular(String yearsSingular) {
        this.yearsSingular = yearsSingular;
    }

    public void setYearsPlural(String yearsPlural) {
        this.yearsPlural = yearsPlural;
    }

    public void setDaysSingular(String daysSingular) {
        this.daysSingular = daysSingular;
    }

    public void setDaysPlural(String daysPlural) {
        this.daysPlural = daysPlural;
    }

    public void setHoursSingular(String hoursSingular) {
        this.hoursSingular = hoursSingular;
    }

    public void setHoursPlural(String hoursPlural) {
        this.hoursPlural = hoursPlural;
    }

    public void setMinutesSingular(String minutesSingular) {
        this.minutesSingular = minutesSingular;
    }

    public void setMinutesPlural(String minutesPlural) {
        this.minutesPlural = minutesPlural;
    }

    public void setSecondsSingular(String secondsSingular) {
        this.secondsSingular = secondsSingular;
    }

    public void setSecondsPlural(String secondsPlural) {
        this.secondsPlural = secondsPlural;
    }

    public void setFormatting(String formatting) {
        this.formatting = formatting;
    }

    // Utility methods
    public String getYearsLabel(int years) {
        return years == 1 ? yearsSingular : yearsPlural;
    }

    public String getDaysLabel(int days) {
        return days == 1 ? daysSingular : daysPlural;
    }

    public String getHoursLabel(int hours) {
        return hours == 1 ? hoursSingular : hoursPlural;
    }

    public String getMinutesLabel(int minutes) {
        return minutes == 1 ? minutesSingular : minutesPlural;
    }

    public String getSecondsLabel(int seconds) {
        return seconds == 1 ? secondsSingular : secondsPlural;
    }

    @Override
    public String toString() {
        return "PlaytimeFormat{" +
                "name='" + name + '\'' +
                ", formatting='" + formatting + '\'' +
                '}';
    }
}