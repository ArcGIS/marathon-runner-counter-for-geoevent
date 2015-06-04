package com.esri.geoevent.processor.marathonrunnercounter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import com.esri.ges.core.Uri;
import com.esri.ges.core.component.ComponentException;
import com.esri.ges.core.geoevent.FieldException;
import com.esri.ges.core.geoevent.FieldExpression;
import com.esri.ges.core.geoevent.GeoEvent;
import com.esri.ges.core.geoevent.GeoEventPropertyName;
import com.esri.ges.core.validation.ValidationException;
import com.esri.ges.framework.i18n.BundleLogger;
import com.esri.ges.framework.i18n.BundleLoggerFactory;
import com.esri.ges.messaging.EventDestination;
import com.esri.ges.messaging.EventUpdatable;
import com.esri.ges.messaging.GeoEventCreator;
import com.esri.ges.messaging.GeoEventProducer;
import com.esri.ges.messaging.Messaging;
import com.esri.ges.messaging.MessagingException;
import com.esri.ges.processor.GeoEventProcessorBase;
import com.esri.ges.processor.GeoEventProcessorDefinition;
import com.esri.ges.util.Converter;

public class RunnerCounter extends GeoEventProcessorBase implements GeoEventProducer, EventUpdatable
{
	private static final BundleLogger		LOGGER				= BundleLoggerFactory.getLogger(RunnerCounter.class);

	private long												reportInterval;

	private final Map<String, String>		trackCache		= new ConcurrentHashMap<String, String>();
	private final Map<String, Counters>	counterCache	    = new ConcurrentHashMap<String, Counters>();

	private Messaging										messaging;
	private GeoEventCreator									geoEventCreator;
	private GeoEventProducer								geoEventProducer;
	private Date											resetTime;
	private boolean											autoResetCounter;
	private boolean											clearCache;
	private Timer											clearCacheTimer;
	private boolean											clearCacheOnStart;	
	private String											categoryField;
	private Uri												definitionUri;
	private String											definitionUriString;
	final Object											lock1					= new Object();
	private ReportGenerator reportGen;
	
	class Counters
	{
		private Long	matCrossedCount	= 0L;
		private Long	onCourseCount	= 0L;

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

		public Long getOnCourseCount()
		{
			return onCourseCount;
		}

		public void setOnCourseCount(Long onCourseCount)
		{
			this.onCourseCount = onCourseCount;
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
				synchronized (lock1)
				{
					counterCache.clear();
					trackCache.clear();
				}
			}
		}
	}

	class ReportGenerator implements Runnable
	{
		private Long	reportInterval	= 5000L;
		private volatile Boolean isRunning;
		
		public ReportGenerator(String category, Long reportInterval)
		{
			this.reportInterval = reportInterval;
		}

		@Override
		public void run()
		{
			isRunning = true;
			while (isRunning)
			{
				try
				{
					Thread.sleep(reportInterval);
					//LOGGER.info("run: " + counterCache.size());

					for (String matId : counterCache.keySet())
					{
						Counters counters = counterCache.get(matId);
						LOGGER.trace("run: " + matId + " crossed = " + counters.matCrossedCount + " on course = " + counters.onCourseCount);
						try
						{
							send(createRunnerCounterGeoEvent(matId, counters));
						}
						catch (MessagingException error)
						{
							LOGGER.error("SEND_ERROR", matId, error.getMessage());
							LOGGER.info(error.getMessage(), error);
						}
					}
				}
				catch (InterruptedException error)
				{
					LOGGER.error(error.getMessage(), error);
				}
			}
		}
		
		public void stop()
		{
			isRunning = false;
		}

		private GeoEvent createRunnerCounterGeoEvent(String matId, Counters counters) throws MessagingException
		{
			//LOGGER.info("createRunnerCounterGeoEvent");
			GeoEvent counterEvent = null;
			if (geoEventCreator != null && definitionUriString != null && definitionUri != null)
			{
				try
				{
					//LOGGER.info("geoEventCreator: createRunnerCounterGeoEvent");
					counterEvent = geoEventCreator.create("RunnerCounter", definitionUriString);
					counterEvent.setField(0, matId);
					counterEvent.setField(1, counters.matCrossedCount);
					counterEvent.setField(2, counters.onCourseCount);
					counterEvent.setField(3, new Date());
					counterEvent.setProperty(GeoEventPropertyName.TYPE, "event");
					counterEvent.setProperty(GeoEventPropertyName.OWNER_ID, getId());
					counterEvent.setProperty(GeoEventPropertyName.OWNER_URI, definitionUri);
				}
				catch (FieldException error)
				{
					counterEvent = null;
					LOGGER.error("CREATE_GEOEVENT_FAILED", error.getMessage());
					LOGGER.info(error.getMessage(), error);
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
		clearCacheOnStart = Converter.convertToBoolean(getProperty("clearCacheOnStart").getValueAsString());
	}

	@Override
	public void setId(String id)
	{
		super.setId(id);
		geoEventProducer = messaging.createGeoEventProducer(new EventDestination(id + ":event"));
	}

	@Override
	public GeoEvent process(GeoEvent geoEvent) throws Exception
	{
		String trackId = geoEvent.getTrackId();
		String previousMat = trackCache.get(trackId);
		String currentMat = (String) geoEvent.getField(new FieldExpression(categoryField)).getValue();

		// Need to synchronize the Concurrent Map on write to avoid wrong counting
		synchronized (lock1)
		{
			// Add or update the status cache
			trackCache.put(trackId, currentMat);
			if (counterCache.containsKey(currentMat) == false)
			{
				counterCache.put(currentMat, new Counters());
			}

			Counters counters = counterCache.get(currentMat);
			LOGGER.trace(currentMat + ": crossed = " + counters.matCrossedCount + ", on course = " + counters.onCourseCount);
			counters.onCourseCount++;
			counters.matCrossedCount++;
			counterCache.put(currentMat, counters);

			// Adjust the previous on-course count when the runner crossed the mat
			if (previousMat != null && currentMat.equals(previousMat) == false)
			{
				Counters previousCounters = counterCache.get(previousMat);
				previousCounters.onCourseCount--;
				counterCache.put(previousMat, previousCounters);
			}
		}

		return null;
	}

	@Override
	public List<EventDestination> getEventDestinations()
	{
		return (geoEventProducer != null) ? Arrays.asList(geoEventProducer.getEventDestination()) : new ArrayList<EventDestination>();
	}

	@Override
	public void validate() throws ValidationException
	{
		super.validate();
		List<String> errors = new ArrayList<String>();
		if (reportInterval <= 0)
			errors.add(LOGGER.translate("VALIDATION_INVALID_REPORT_INTERVAL", definition.getName()));
		if (errors.size() > 0)
		{
			StringBuffer sb = new StringBuffer();
			for (String message : errors)
				sb.append(message).append("\n");
			throw new ValidationException(LOGGER.translate("VALIDATION_ERROR", this.getClass().getName(), sb.toString()));
		}
	}

	@Override
	public synchronized void onServiceStart()
	{
		if (clearCacheOnStart == true)
		{
			synchronized (lock1)
			{
				trackCache.clear();
				counterCache.clear();
				
				LOGGER.info("Clear cache on Start -- TrackCache Size: " + trackCache.size() + " CounterCache Size: " + counterCache.size());

			}
		}
		if (this.autoResetCounter == true || this.clearCache == true)
		{
			if (clearCacheTimer == null)
			{
				// Get the Date corresponding to 11:01:00 pm today.
				Calendar calendar1 = Calendar.getInstance();
				calendar1.setTime(resetTime);
				Date time1 = calendar1.getTime();

				clearCacheTimer = new Timer();
				Long dayInMilliSeconds = 60 * 60 * 24 * 1000L;
				clearCacheTimer.scheduleAtFixedRate(new ClearCacheTask(), time1, dayInMilliSeconds);
			}
		}

		if (definition != null)
		{
			definitionUri = definition.getUri();
			definitionUriString = definitionUri.toString();
		}
		if (reportGen == null)
			reportGen = new ReportGenerator(categoryField, reportInterval);
		Thread thread = new Thread(reportGen);
		thread.setName("Runner Counter Report Generator");
		thread.start();
	}

	@Override
	public synchronized void onServiceStop()
	{
		if (clearCacheTimer != null)
		{
			clearCacheTimer.cancel();
		}
		if (reportGen != null)
			reportGen.stop();
	}

	@Override
	public void shutdown()
	{
		onServiceStop();
		super.shutdown();
	}

	@Override
	public EventDestination getEventDestination()
	{
		return (geoEventProducer != null) ? geoEventProducer.getEventDestination() : null;
	}

	@Override
	public void send(GeoEvent geoEvent) throws MessagingException
	{
		if (geoEventProducer != null && geoEvent != null)
		{
			geoEventProducer.send(geoEvent);
			//LOGGER.info("send: " + geoEvent.toString());
		}
	}

	public void setMessaging(Messaging messaging)
	{
		this.messaging = messaging;
		geoEventCreator = messaging.createGeoEventCreator();
	}

	@Override
	public void disconnect()
	{
		if (geoEventProducer != null)
			geoEventProducer.disconnect();
	}

	@Override
	public String getStatusDetails()
	{
		return (geoEventProducer != null) ? geoEventProducer.getStatusDetails() : "";
	}

	@Override
	public void init() throws MessagingException
	{
		afterPropertiesSet();
	}

	@Override
	public boolean isConnected()
	{
		return (geoEventProducer != null) ? geoEventProducer.isConnected() : false;
	}

	@Override
	public void setup() throws MessagingException
	{
		;
	}

	@Override
	public void update(Observable o, Object arg)
	{
		;
	}
}
