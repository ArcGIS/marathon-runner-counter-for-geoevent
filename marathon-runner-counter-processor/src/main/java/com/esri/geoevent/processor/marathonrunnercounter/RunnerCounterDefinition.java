/*
  Copyright 2017 Esri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.​

  For additional information, contact:
  Environmental Systems Research Institute, Inc.
  Attn: Contracts Dept
  380 New York Street
  Redlands, California, USA 92373

  email: contracts@esri.com
*/

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
			propertyDefinitions.put("clearCacheOnStart", new PropertyDefinition("clearCacheOnStart", PropertyType.Boolean, false, "Clear Cache on Start", "Clear Cache on Start", true, false));
			// TODO: How about TrackId selection to potentially track only a
			// subset of geoevents ???
			GeoEventDefinition ged = new DefaultGeoEventDefinition();
			ged.setName("RunnerCounter");
			List<FieldDefinition> fds = new ArrayList<FieldDefinition>();
			fds.add(new DefaultFieldDefinition("MatId", FieldType.String, "TRACK_ID"));
			fds.add(new DefaultFieldDefinition("MatCrossedCount", FieldType.Long));
			fds.add(new DefaultFieldDefinition("OnCourseCount", FieldType.Long));
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
