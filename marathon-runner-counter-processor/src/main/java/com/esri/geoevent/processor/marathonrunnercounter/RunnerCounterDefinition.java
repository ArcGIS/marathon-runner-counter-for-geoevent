package com.esri.geoevent.processor.marathonrunnercounter;

import java.util.ArrayList;
import java.util.List;

import com.esri.ges.core.geoevent.DefaultFieldDefinition;
import com.esri.ges.core.geoevent.DefaultGeoEventDefinition;
import com.esri.ges.core.geoevent.FieldDefinition;
import com.esri.ges.core.geoevent.FieldType;
import com.esri.ges.core.geoevent.GeoEventDefinition;
import com.esri.ges.core.property.PropertyDefinition;
import com.esri.ges.core.property.PropertyType;
import com.esri.ges.framework.i18n.BundleLogger;
import com.esri.ges.framework.i18n.BundleLoggerFactory;
import com.esri.ges.processor.GeoEventProcessorDefinitionBase;

public class RunnerCounterDefinition extends GeoEventProcessorDefinitionBase
{
	private static final BundleLogger	LOGGER	= BundleLoggerFactory.getLogger(RunnerCounterDefinition.class);

	public RunnerCounterDefinition()
	{
		try
		{
			propertyDefinitions.put("reportInterval", new PropertyDefinition("reportInterval", PropertyType.Long, 10, "Report Interval (seconds)", "Report Interval (seconds)", false, false));
			propertyDefinitions.put("categoryField", new PropertyDefinition("categoryField", PropertyType.String, "MatId", "Category Field Name", "Category Field Name", false, false));
			propertyDefinitions.put("autoResetCounter", new PropertyDefinition("autoResetCounter", PropertyType.Boolean, false, "Automatic Reset Counter", "Auto Reset Counter", true, false));
			propertyDefinitions.put("resetTime", new PropertyDefinition("resetTime", PropertyType.String, "00:00:00", "Reset Counter to Zero at", "Reset Counter time", "autoResetCounter=true", false, false));
			propertyDefinitions.put("clearCache", new PropertyDefinition("clearCache", PropertyType.Boolean, true, "Clear in-memory Cache", "Clear in-memory Cache", "autoResetCounter=true", false, false));
			// TODO: How about TrackId selection to potentially track only a
			// subset of geoevents ???
			GeoEventDefinition ged = new DefaultGeoEventDefinition();
			ged.setName("RunnerCounter");
			List<FieldDefinition> fds = new ArrayList<FieldDefinition>();
			fds.add(new DefaultFieldDefinition("MatId", FieldType.String, "TRACK_ID"));
			fds.add(new DefaultFieldDefinition("MatCrossedCount", FieldType.Long));
			fds.add(new DefaultFieldDefinition("TweenCount", FieldType.Long));
			fds.add(new DefaultFieldDefinition("LastReceived", FieldType.Date));
			fds.add(new DefaultFieldDefinition("Geometry", FieldType.Geometry));
			ged.setFieldDefinitions(fds);
			geoEventDefinitions.put(ged.getName(), ged);
		}
		catch (Exception error)
		{
			LOGGER.error("INIT_ERROR", error.getMessage());
			LOGGER.info(error.getMessage(), error);
		}
	}

	@Override
	public String getName()
	{
		return "RunnerCounter";
	}

	@Override
	public String getDomain()
	{
		return "com.esri.geoevent.processor";
	}

	@Override
	public String getVersion()
	{
		return "10.3.0";
	}

	@Override
	public String getLabel()
	{
		return "${com.esri.geoevent.processor.marathon-runner-counter-processor.PROCESSOR_LABEL}";
	}

	@Override
	public String getDescription()
	{
		return "${com.esri.geoevent.processor.marathon-runner-counter-processor.PROCESSOR_DESC}";
	}
}
