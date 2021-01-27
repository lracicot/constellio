FROM gradle:4.7.0-jdk8-alpine AS builder
COPY --chown=gradle:gradle . /home/gradle/constellio

WORKDIR /home/gradle


COPY gradle.properties.main gradle.properties
COPY build.gradle.main build.gradle
COPY settings.gradle.main settings.gradle
COPY version version

RUN gradle war --no-daemon
RUN mkdir webapp && mv constellio/build/libs/constellio-*.war webapp
RUN (cd webapp && unzip constellio-*.war && rm -f constellio-*.war)

FROM centos:8

RUN yum install -y java-1.8.0-openjdk unzip libXinerama cairo cups

WORKDIR /opt

# Install libreoffice
RUN curl -O http://downloadarchive.documentfoundation.org/libreoffice/old/5.3.7.2/rpm/x86_64/LibreOffice_5.3.7.2_Linux_x86-64_rpm.tar.gz
RUN tar -xvf LibreOffice_5.3.7.2_Linux_x86-64_rpm.tar.gz
RUN rpm -ivh LibreOffice_*_rpm/RPMS/*.rpm
RUN ln -s /opt/libreoffice* /opt/libreoffice

EXPOSE 8080

COPY docker /opt/constellio/
COPY --from=builder /home/gradle/webapp /opt/constellio/webapp/

RUN mkdir -p /opt/solr/bin/
RUN echo 'SOLR_JAVA_MEM="-Xms512m -Xmx4096m"' > /opt/solr/bin/solr.in.sh

RUN ["chmod", "+x", "/opt/docker-environment"]
RUN ["chmod", "u+x", "/opt/constellio/startup"]

ENTRYPOINT ["/opt/constellio/docker-environment"]
CMD ["/opt/constellio/startup", "start"]
