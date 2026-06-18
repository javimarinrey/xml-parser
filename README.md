# Lectura XML

Uso de la libería StAX (streaming pull parser) con una capa de routing por tipo, complementada con un pool de parsers para reducir el overhead de instanciación.

StAX es pull: tú controlas el cursor, puedes parar después del primer tag que te interese y reanudar el mismo stream.

Para añadir un nuevo tipo XML solo necesitas dos archivos y una línea:

- Crear el record BajaRequest.java en model/
- Crear BajaParser.java implementando XmlParser<BajaRequest>
- Añadir new BajaParser() en la lista de AppInitializer.java

## Compilar
```shell
mvn clean package
``` 

## Copia el WAR a Tomcat 10+ (Jakarta EE)

```shell
cp target/xml-parser.war $TOMCAT_HOME/webapps/
```

## Ejecutar

```shell
curl -X POST http://localhost:8080/xml-parser/api/xml \
-H "Content-Type: application/xml" \
-d '<?xml version="1.0"?><solicitud><tipo>CONSULTA</tipo><id>42</id><filtro>activo</filtro></solicitud>'
```

Para sacar p99 de latencia total con Unix estándar:

```shell
awk '/METRICS/ {print $7}' xml-metrics.log \
  | sed 's/total_us=//' \
  | sort -n \
  | awk 'BEGIN{c=0} {a[c++]=$1} END{print a[int(c*0.99)]}'
``