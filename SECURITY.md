# Security

## Reporting

Use GitHub private vulnerability reporting for this repository:

https://github.com/martinlofranodeoliveira/mask-region-core/security/advisories/new

Please do not disclose exploitable details in public issues. Provide a minimal synthetic reproducer, affected development version, JDK version, fixed error code and an explanation of the boundary affected. Do not send personal images, credentials, secrets, private client code or unnecessary host paths. No response-time SLA is offered.

For general contact, use the [official Lofrano Tecnologia homepage](https://lofranotecnologia.com.br).

## Scope and limits

Version 0.1.0 is a development version; there is no production-readiness or security-certification claim. The library contains no telemetry, network clients, secrets or runtime model/API dependency.

Core operations validate dimensions and conservative logical scratch plans before operation-buffer allocation. Caller arrays and BitSets are copied within validated ranges. Error codes do not expose pixels, filenames or raw decoder messages. This does not convert JVM allocation errors into recoverable library errors or impose a process memory ceiling.

The PNG adapter caps compressed input at 4 MiB, preflights dimensions and the supported raw model before decode, and uses explicit memory image streams. ImageIO codec internals and JVM overhead are outside algorithm scratch accounting. Ignore-metadata options do not guarantee that a decoder never parses ancillary chunks. ImageIO is not a hardened hostile-image sandbox or a full PNG validator. Keep the JDK updated; untrusted images may require separate process isolation, external resource limits and caller-controlled IO timeouts beyond this library.

Caller streams remain open and failed writes may leave partial output. Blocking streams require caller cancellation. The CLI reads no user paths: it creates and removes only its own synthetic temporary files. It is not an image-upload service, clinical system or physical measurement tool.
