package com.esri.geoevent.processor.marathonrunnercounter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.esri.ges.core.Uri;
import com.esri.ges.core.component.ComponentException;
import com.esri.ges.core.geoevent.FieldException;
import com.esri.ges.core.geoevent.FieldExpression;
import com.esri.ges.core.geoevent.GeoEvent;
import com.esri.ges.core.geoevent.GeoEventPropertyName;
import com.esri.ges.core.validation.ValidationException;
import com.esri.ges.messaging.EventDestination;
import com.esri.ges.messaging.EventProducer;
import com.esri.ges.messaging.EventUpdatable;
import com.esri.ges.messaging.GeoEventCreator;
import com.esri.ges.messaging.GeoEventProducer;
import com.esri.ges.messaging.Messaging;
import com.esri.ges.messaging.MessagingException;
import com.esri.ges.processor.GeoEventProcessorBase;
import com.esri.ges.processor.GeoEventProcessorDefinition;
import com.esri.ges.util.Converter;

public class RunnerCounter extends GeoEventProcessorBase implements EventProducer, EventUpdatable
{
  private static final Log                     log                      = LogFactory.getLog(RunnerCounter.class);
  private long                                 reportInterval;

  private final Map<String, String>            trackCache               = new ConcurrentHashMap<String, String>();
  private final Map<String, Counters>          counterCache             = new ConcurrentHashMap<String, Counters>();

  private Messaging                            messaging;
  private GeoEventCreator                      geoEventCreator;
  private GeoEventProducer                     geoEventProducer;
  private EventDestination                     destination;
  private Date                                 resetTime;
  private boolean                              autoResetCounter;
  private boolean                              clearCache;
  private Timer                                clearCacheTimer;
  private String                               categoryField;
  private Boolean                              isCounting = false;
  private Uri                                  definitionUri;
  private String                               definitionUriString;
  final   Object                               lock1 = new Object();

  class Counters
  {
    private Long matCrossedCount = 0L;
    private Long tweenCount = 0L;
    
    public Counters()
    {
      
    }
    
    public Long getMatCrossedCount()
    {
      return matCrossedCount;
    }
    
    public void setMatCrossedCount(Long matCrossedCount)
    {
      this.matCrossedCount = matCrossedCount;
    }
    
    public Long getTweenCount()
    {
      return tweenCount;
    }
    
    public void setTweenCount(Long tweenCount)
    {
      this.tweenCount = tweenCount;
    }
  }
  
  class ClearCacheTask extends TimerTask
  {
    public void run()
    {
      if (autoResetCounter == true)
      {
        for (String matId : counterCache.keySet())
        {
          Counters counters = new Counters();
          counterCache.put(matId, counters);
        }
      }
      // clear the cache
      if (clearCache == true)
      {
        counterCache.clear();
        trackCache.clear();
      }
    }
  }
  
  class ReportGenerator implements Runnable
  { 
    private Long reportInterval = 5000L;
    
    public ReportGenerator(String category, Long reportInterval) 
    {
      this.reportInterval = reportInterval;
    }
  
    @Override
    public void run()
    {
      while (isCounting)
      {
        try
        {
          Thread.sleep(reportInterval);
          for (String matId : counterCache.keySet())
          {
            Counters counters = counterCache.get(matId);
            try
            {
              send(createRunnerCounterGeoEvent(matId, counters));
            }
            catch (MessagingException e)
            {
              log.error("Error sending update GeoEvent for " + matId, e);
            }
          }
        }
        catch (InterruptedException e1)
        {
          log.error(e1);
        }       
      }
    }
    
    private GeoEvent createRunnerCounterGeoEvent(String matId, Counters counters) throws MessagingException
    {
      GeoEvent counterEvent = null;
      if (geoEventCreator != null && definitionUriString != null && definitionUri != null)
      {
        try
        {
          counterEvent = geoEventCreator.create("RunnerCounter", definitionUriString);
          counterEvent.setField(0, matId);
          counterEvent.setField(1, counters.matCrossedCount);
          counterEvent.setField(2, counters.tweenCount);
          counterEvent.setField(3, new Date());
          counterEvent.setProperty(GeoEventPropertyName.TYPE, "event");
          counterEvent.setProperty(GeoEventPropertyName.OWNER_ID, getId());
          counterEvent.setProperty(GeoEventPropertyName.OWNER_URI, definitionUri);
        }
        catch (FieldException e)
        {
          counterEvent = null;
          log.error("Failed to create Runner Count GeoEvent: " + e.getMessage());
        }
      }
      return counterEvent;
    }   
  }

  protected RunnerCounter(GeoEventProcessorDefinition definition) throws ComponentException
  {
    super(definition);
  }

  public void afterPropertiesSet()
  {
    reportInterval = Converter.convertToInteger(getProperty("reportInterval").getValueAsString(), 10) * 1000;
    categoryField = getProperty("categoryField").getValueAsString();
    autoResetCounter = Converter.convertToBoolean(getProperty("autoResetCounter").getValueAsString());
    String[] resetTimeStr = getProperty("resetTime").getValueAsString().split(":");
    // Get the Date corresponding to 11:01:00 pm today.
    Calendar calendar = Calendar.getInstance();
    calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(resetTimeStr[0]));
    calendar.set(Calendar.MINUTE, Integer.parseInt(resetTimeStr[1]));
    calendar.set(Calendar.SECOND, Integer.parseInt(resetTimeStr[2]));
    resetTime = calendar.getTime();
    clearCache = Converter.convertToBoolean(getProperty("clearCache").getValueAsString());   
  }

  @Override
  public void setId(String id)
  {
    super.setId(id);
    destination = new EventDestination(getId() + ":event");
    geoEventProducer = messaging.createGeoEventProducer(destination.getName());
  }
  
  @Override
  public GeoEvent process(GeoEvent geoEvent) throws Exception
  {
    String trackId = geoEvent.getTrackId();
    String previousMat = trackCache.get(trackId);
    String currentMat = (String) geoEvent.getField(new FieldExpression(categoryField)).getValue();

    // Need to synchronize the Concurrent Map on write to avoid wrong counting
    synchronized(lock1)
    {
      // Add or update the status cache
      trackCache.put(trackId, currentMat);
      if (!counterCache.containsKey(currentMat))
      {
        counterCache.put(currentMat, new Counters());
      }
    
      Counters counters = counterCache.get(currentMat);
      counters.tweenCount++;
      counters.matCrossedCount++;
      counterCache.put(currentMat, counters);
    
      // Adjust the previous tween count when the runner crossed the mat
      if (previousMat != null && !currentMat.equals(previousMat))
      {
        Counters previousCounters = counterCache.get(previousMat);
        previousCounters.tweenCount--;
        counterCache.put(previousMat,  previousCounters);
      }
    }
       
    return null;
  }

  @Override
  public List<EventDestination> getEventDestinations()
  {
    return Arrays.asList(destination);
  }

  @Override
  public void validate() throws ValidationException
  {
    super.validate();
    List<String> errors = new ArrayList<String>();
    if (reportInterval <= 0)
      errors.add("'" + definition.getName() + "' property 'reportInterval' is invalid.");
    if (errors.size() > 0)
    {
      StringBuffer sb = new StringBuffer();
      for (String message : errors)
        sb.append(message).append("\n");
      throw new ValidationException(this.getClass().getName() + " validation failed: " + sb.toString());
    }
  }

  @Override
  public void onServiceStart()
  {
    if (this.autoResetCounter == true || this.clearCache == true)
    {
      if (clearCacheTimer == null)
      {
        // Get the Date corresponding to 11:01:00 pm today.
        Calendar calendar1 = Calendar.getInstance();
        calendar1.setTime(resetTime);
        Date time1 = calendar1.getTime();

        clearCacheTimer = new Timer();
        Long dayInMilliSeconds = 60*60*24*1000L;
        clearCacheTimer.scheduleAtFixedRate(new ClearCacheTask(), time1, dayInMilliSeconds);
      }
      trackCache.clear();
      counterCache.clear();
    }
   
    isCounting = true;
    if (definition != null)
    {
      definitionUri = definition.getUri();
      definitionUriString = definitionUri.toString();      
    }
    
    ReportGenerator reportGen = new ReportGenerator(categoryField, reportInterval);
    Thread t = new Thread(reportGen);
    t.setName("Runner Counter Report Generator");
    t.start();
  }

  @Override
  public void onServiceStop()
  {
    if (clearCacheTimer != null)
    {
      clearCacheTimer.cancel();
    }
    isCounting = false;
  }

  @Override
  public void shutdown()
  {
    super.shutdown();
    
    if (clearCacheTimer != null)
    {
      clearCacheTimer.cancel();
    }
  }

  @Override
  public EventDestination getEventDestination()
  {
    return destination;
  }

  @Override
  public void send(GeoEvent geoEvent) throws MessagingException
  {
    //Try to get it again
    if (geoEventProducer == null)
    {
      destination = new EventDestination(getId() + ":event");
      geoEventProducer = messaging.createGeoEventProducer(destination.getName());      
    }
    if (geoEventProducer != null && geoEvent != null)
    {
      geoEventProducer.send(geoEvent);
    }
  }

  public void setMessaging(Messaging messaging)
  {
    this.messaging = messaging;
    geoEventCreator = messaging.createGeoEventCreator();
  }
}
