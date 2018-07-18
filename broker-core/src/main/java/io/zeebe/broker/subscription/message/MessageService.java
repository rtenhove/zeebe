/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.subscription.message;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.logstreams.processor.KeyGenerator;
import io.zeebe.broker.logstreams.processor.StreamProcessorIds;
import io.zeebe.broker.logstreams.processor.StreamProcessorServiceFactory;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.subscription.message.processor.OpenMessageSubscriptionProcessor;
import io.zeebe.broker.subscription.message.processor.PublishMessageProcessor;
import io.zeebe.broker.subscription.message.state.MessageDataStore;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.transport.ServerTransport;

public class MessageService implements Service<MessageService> {

  private final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
  private final Injector<StreamProcessorServiceFactory> streamProcessorServiceFactoryInjector =
      new Injector<>();

  private final ServiceGroupReference<Partition> partitionsGroupReference =
      ServiceGroupReference.<Partition>create()
          .onAdd((partitionName, partition) -> startStreamProcessors(partitionName, partition))
          .build();

  private void startStreamProcessors(
      ServiceName<Partition> partitionServiceName, Partition partition) {
    final ServerTransport transport = clientApiTransportInjector.getValue();
    final StreamProcessorServiceFactory factory = streamProcessorServiceFactoryInjector.getValue();

    final TypedStreamEnvironment env =
        new TypedStreamEnvironment(partition.getLogStream(), transport.getOutput());

    factory
        .createService(partition, partitionServiceName)
        .processor(createStreamProcessors(env))
        .processorId(StreamProcessorIds.MESSAGE_PROCESSOR_ID)
        .processorName("message")
        .build();
  }

  private TypedStreamProcessor createStreamProcessors(TypedStreamEnvironment env) {

    final MessageDataStore messageDataStore = new MessageDataStore();

    return env.newStreamProcessor()
        .keyGenerator(new KeyGenerator(0, 1))
        .onCommand(
            ValueType.MESSAGE, MessageIntent.PUBLISH, new PublishMessageProcessor(messageDataStore))
        .onCommand(
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.OPEN,
            new OpenMessageSubscriptionProcessor())
        .withStateResource(messageDataStore)
        .build();
  }

  @Override
  public MessageService get() {
    return this;
  }

  public Injector<ServerTransport> getClientApiTransportInjector() {
    return clientApiTransportInjector;
  }

  public ServiceGroupReference<Partition> getPartitionsGroupReference() {
    return partitionsGroupReference;
  }

  public Injector<StreamProcessorServiceFactory> getStreamProcessorServiceFactoryInjector() {
    return streamProcessorServiceFactoryInjector;
  }
}
