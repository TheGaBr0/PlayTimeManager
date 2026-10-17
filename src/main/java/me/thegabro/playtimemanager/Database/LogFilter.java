package me.thegabro.playtimemanager.Database;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

public class LogFilter extends AbstractFilter
{
    public static void registerFilter()
    {
        // Only the log4j-core backend supports filters (e.g. not when the plugin runs under test)
        if (LogManager.getRootLogger() instanceof Logger logger) {
            logger.addFilter(new LogFilter());
        }
    }

    @Override
    public Result filter(LogEvent event)
    {
        if(event == null)
        {
            return Result.NEUTRAL;
        }
        if(event.getLoggerName().contains("Hikari"))
        {
            return Result.DENY;
        }
        return Result.NEUTRAL;
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t)
    {
        return Result.NEUTRAL;
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params)
    {
        return Result.NEUTRAL;
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t)
    {
        return Result.NEUTRAL;
    }
}