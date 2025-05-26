# HLS Demo Server

This Spring Boot project demonstrates how to proxy HLS playlists and segments from a
remote origin while inserting ad breaks.

Requires **Java 21** to build and run.

### Configuration

Edit `server/src/main/resources/application.properties` to point to your origin
server and configure ad behaviour:

```
hls.origin-base-url=http://localhost:8081/hls
# hls.ad-base-url=http://localhost:8081/hls/ads
hls.ad-frequency-minutes=2
hls.segment-duration-seconds=5
transcoder.output-path=live
```
The configured `transcoder.output-path` will be combined with the stream name so
HLS files are written under `<output-path>/<stream-name>`.

### Running

```
cd server
mvn spring-boot:run
```

Requests to `/hls/{stream}.m3u8` and `/hls/{segment}.ts` will be fetched from the
origin server. You can also request a specific quality using
`/hls/{quality}/{stream}.m3u8` and `/hls/{quality}/{segment}.ts`.
Ad segments are available per quality at `/hls/ads/{quality}/{segment}.ts`.
After a user has streamed for the configured duration, the playlist returned to
that user will contain a discontinuity and three ad segments of the requested
quality.
