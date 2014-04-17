package com.esri.geoevent.processor.marathonrunnercounter;

import com.esri.ges.core.component.ComponentException;
import com.esri.ges.messaging.Messaging;
import com.esri.ges.processor.GeoEventProcessor;
import com.esri.ges.processor.GeoEventProcessorServiceBase;

public class RunnerCounterService extends GeoEventProcessorServiceBase
{
  private Messaging messaging;

  public RunnerCounterService()
  {
    definition = new RunnerCounterDefinition();
  }

  @Override
  public GeoEventProcessor create() throws ComponentException
  {
    RunnerCounter detector = new RunnerCounter(definition);
    detector.setMessaging(messaging);
    return detector;
  }

  public void setMessaging(Messaging messaging)
  {
    this.messaging = messaging;
  }
}