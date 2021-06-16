FROM shipilev/openjdk:16

MAINTAINER @agafox <agafox@sip3.io>
MAINTAINER @windsent <windsent@sip3.io>

RUN apt-get update && \
    apt-get install openssl

ENV SERVICE_NAME sip3-twig
ENV HOME /opt/$SERVICE_NAME

ENV EXECUTABLE_FILE $HOME/$SERVICE_NAME.jar
ADD target/$SERVICE_NAME*.jar $EXECUTABLE_FILE

ENV CONFIG_FILE $HOME/application.yml
ADD src/main/resources/application.yml $CONFIG_FILE

ENV LOGBACK_FILE $HOME/logback.xml
ADD src/main/resources/logback.xml $LOGBACK_FILE

ENV JAVA_OPTS "-Xms128m -Xmx256m"
ENTRYPOINT java $JAVA_OPTS -jar $EXECUTABLE_FILE --spring.config.location=classpath:/application.yml,$CONFIG_FILE --logging.config=$LOGBACK_FILE