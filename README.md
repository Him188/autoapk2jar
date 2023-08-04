# autoapk2jar

Converts apk to jar then decompile using fernflower and finally creates a gradle android project for it.

## Usage
```bash
./gradlew distZip distTar
unzip build/distributions/autoapk2jar-<version>.zip
build/dist/autoapk2jar-<version>/bin/autoapk2jar
```

## Version
dex2jar - v2.1  
fernfower - https://github.com/fesh0r/fernflower/tree/1ebdb71dec45093e0e94194c85de2602b405931b  
