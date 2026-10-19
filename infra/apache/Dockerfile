# Apache httpd 2.4 in front of the Tomcat of the lessor extranet.
#   docker build -t fleetpilot/apache infra/apache
FROM httpd:2.4

# Modules of the reverse proxy, the headers, the compression and the client address; the configuration of FleetPilot
# is included at the end of the stock httpd.conf
RUN sed -i \
        -e 's/^#\(LoadModule proxy_module .*\)/\1/' \
        -e 's/^#\(LoadModule proxy_http_module .*\)/\1/' \
        -e 's/^#\(LoadModule headers_module .*\)/\1/' \
        -e 's/^#\(LoadModule deflate_module .*\)/\1/' \
        -e 's/^#\(LoadModule remoteip_module .*\)/\1/' \
        -e 's/^\(CustomLog .*\)/#\1/' \
        -e 's/^\(ErrorLog .*\)/#\1/' \
        /usr/local/apache2/conf/httpd.conf \
    && echo 'Include conf/extra/fleetpilot-extranet.conf' >> /usr/local/apache2/conf/httpd.conf \
    && echo ok > /usr/local/apache2/htdocs/healthz.txt

COPY conf/fleetpilot-extranet.conf /usr/local/apache2/conf/extra/fleetpilot-extranet.conf
COPY htdocs/maintenance.html /usr/local/apache2/htdocs/maintenance.html

HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD ["bash", "-c", "exec 3<>/dev/tcp/localhost/80 && printf 'GET /healthz HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q ok <&3"]
