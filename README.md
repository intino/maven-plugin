# intino-maven-plugin

Plugin Maven que sustituye las operaciones del plugin de Intino:

| Goal | Fase por defecto | Función |
| --- | --- | --- |
| `intino:import` | `initialize` | Convierte los `.legio` del módulo Maven y de los clientes hermanos declarados como `Service ... as UI`; genera `pom.xml` o `package.json` y extrae los `WebArtifact`. |
| `intino:export` | `generate-sources` | Ejecuta Konos en modo `Export` para generar accessors. |
| `intino:compile` | `compile` | Ejecuta el ciclo Maven hasta `compile`: Konos y la compilación Java. |
| `intino:compile-ui` | — (manual) | Ejecuta Konos en modo `OnlyElements`: genera únicamente los clientes hermanos declarados por `Service ... as UI`. |
| `intino:package` | `package` | Ejecuta el ciclo Maven hasta `package`: compila Java, genera la UI y crea el JAR. |
| `intino:release` | `deploy` | Ejecuta el ciclo Maven completo hasta `deploy` y publica el proyecto Maven actual. |

`initialize` es la fase más temprana útil. Para una migración de un reactor, ejecute primero `mvn io.intino:intino-maven-plugin:1.0.0:import` desde cada módulo (o con `-pl`); Maven ya ha leído los POM del reactor antes de ejecutar cualquier fase, por lo que los POM recién creados no pueden incorporarse al mismo reactor.

## Varios módulos servidores

`intino:import` no es agregador: se ejecuta una vez por cada módulo Maven servidor. Solo procesa el `artifact.legio` situado en el directorio de ese módulo y los clientes hermanos declarados por sus `Service ... as UI`; nunca recorre los módulos hijos del POM raíz.

Declare los servidores en un reactor Maven. Los clientes no necesitan ser módulos Maven, pues el plugin los localiza como directorios hermanos del servidor.

```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <packaging>pom</packaging>
  <modules>
    <module>inventario</module>
    <module>facturacion</module>
  </modules>
</project>
```

Configure el plugin en cada POM servidor —o en un padre, dentro de `pluginManagement`, para compartir la versión y la configuración—. Para procesarlos todos desde la raíz, use el ciclo Maven normal:

```bash
mvn clean package
```

Para elegir uno o varios módulos, use el selector nativo de Maven:

```bash
# Solo inventario
mvn -pl :inventario intino:compile

# Varios módulos
mvn -pl :inventario,:facturacion intino:package

# Un módulo y sus dependencias Maven previas
mvn -pl :inventario -am intino:package
```

Tras crear o cambiar los POM durante una migración, ejecute `mvn -pl :inventario,:facturacion intino:import` una vez para regenerarlos y después recargue el reactor en IntelliJ.

## Uso

Maven resuelve los plugins en `pluginRepositories`, que es distinto de `repositories`. Declare el repositorio de plugins de Intino antes de usar el prefijo `intino`; si no se declara, `mvn intino:...` no puede encontrar el plugin aunque `release-libraries` esté en `repositories`.

```xml
<pluginRepositories>
  <pluginRepository>
    <id>intino-maven</id>
    <url>https://artifactory.intino.io/artifactory/release-libraries</url>
  </pluginRepository>
</pluginRepositories>
```

Los convertidores y Konos se resuelven en los repositorios que declare el POM consumidor (por ejemplo, `release-libraries`). Las versiones se dejan explícitas para no acoplar el plugin a una versión de Intino.

```xml
<plugin>
  <groupId>io.intino</groupId>
  <artifactId>intino-maven-plugin</artifactId>
  <version>1.0.0</version>
  <configuration>
    <legio2mavenVersion>1.2.6</legio2mavenVersion>
    <legio2packagejsonVersion>1.3.5</legio2packagejsonVersion>
    <intinoBuilderVersion>14.3.5</intinoBuilderVersion>
    <!-- Recomendado si ya hay una configuración Konos conocida -->
    <argumentsFile>${project.basedir}/konos.args</argumentsFile>
  </configuration>
  <executions>
    <execution><goals><goal>import</goal><goal>export</goal><goal>generate</goal></goals></execution>
  </executions>
</plugin>
```

## Primera migración de un proyecto Legio

Maven necesita un `pom.xml` para poder ejecutar un plugin. Si el proyecto todavía solo tiene `artifact.legio`, cree temporalmente este `pom.xml` en el directorio del módulo servidor:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>migration</groupId>
  <artifactId>bootstrap</artifactId>
  <version>1.0.0</version>
  <repositories>
    <repository>
      <id>intino-release-libraries</id>
      <url>https://artifactory.intino.io/artifactory/release-libraries</url>
    </repository>
  </repositories>
  <pluginRepositories>
    <pluginRepository>
      <id>intino-maven</id>
      <url>https://artifactory.intino.io/artifactory/release-libraries</url>
    </pluginRepository>
  </pluginRepositories>
  <build><plugins><plugin>
    <groupId>io.intino</groupId>
    <artifactId>intino-maven-plugin</artifactId>
    <version>1.0.0</version>
    <configuration>
      <legio2mavenVersion>1.2.6</legio2mavenVersion>
      <legio2packagejsonVersion>1.3.5</legio2packagejsonVersion>
      <intinoBuilderVersion>14.2.2</intinoBuilderVersion>
    </configuration>
  </plugin></plugins></build>
</project>
```

Instale antes el plugin localmente (`cd intino-maven-plugin && mvn install`) o publique una versión no-SNAPSHOT en su repositorio Maven. Después ejecute `mvn intino:import`. El conversor reemplaza el POM temporal por el POM migrado, pero conserva cada `.legio`: es la fuente de verdad y en cada importación se regenera el `pom.xml` o el `package.json`. Revise los cambios en Git antes de continuar con `mvn compile`.

## Distribuir el plugin en Artifactory Intino

El propio plugin se publica en Artifactory Intino: las versiones normales van a `release-libraries` y las terminadas en `-SNAPSHOT` a `snapshot-libraries`. Configure las credenciales bajo el servidor `intino-maven` en `~/.m2/settings.xml` y, desde el directorio del plugin, ejecute:

```bash
mvn intino:release
```

Para reconstruir desde cero antes de publicar, use `mvn clean intino:release`. El goal ejecuta las fases Maven hasta `deploy` para el proyecto Maven desde el que se invoca. Al usarlo en `intino-maven-plugin` publica el plugin; al usarlo en un módulo Intino migrado, publica ese módulo.

En los proyectos migrados, el goal interno `intino:package-ui` queda asociado a `prepare-package`. Antes de crear el JAR evalúa si hace falta generar cada `Service ... as UI`: compara sus fuentes con el contenido de `output.path` de `webpack.config.js` y solo ejecuta `npm run build` si hay cambios. Después copia el directorio resultante completo bajo `www` en el directorio de clases del servidor. Por ejemplo, un `output.path` acabado en `www/inventario-elements` se incorpora como `www/inventario-elements/...` al JAR. Puede adaptar el comando con `-Dintino.npmExecutable=pnpm` o `-Dintino.buildScript=build:production`; use `-Dintino.forceUiBuild=true` para forzar la regeneración.

El importador revisa el módulo Maven y busca `Service Nombre as UI` o `Service Nombre is UI` en sus `.konos` y `.tara`. Cada nombre se convierte a kebab-case para encontrar el cliente hermano: por ejemplo, `InventarioElements` resuelve `${project.basedir}/../inventario-elements`. `legioDirectory` permite añadir un cliente fuera de esa convención. Reconoce `WebImports` como módulo cliente y extrae cada línea de la forma `WebArtifact("group", "artifact", "version") nombre` en `node_modules/nombre`. Los artefactos se resuelven usando los repositorios Maven declarados por el consumidor. Los `.legio` nunca se eliminan.

`WebArtifact` no se publica en npm: se resuelve como un JAR Maven y se extrae en `.intino/web-artifacts/<alias>-<versión>`. El plugin añade al `package.json` una dependencia local, por ejemplo `"alexandria-ui-elements": "file:.intino/web-artifacts/alexandria-ui-elements-7.4.25"`. Después, `npm install` o `pnpm install` lo instala en `node_modules` sin consultar npm. Al cambiar la versión en el `.legio`, cambia también esa ruta y el gestor de paquetes actualiza el artefacto. `intino:resolve-web-artifacts` vuelve a descargar los directorios locales si se limpian.

El origen se configura como cualquier repositorio Maven del proyecto:

```xml
<repositories>
  <repository>
    <id>intino-release-libraries</id>
    <url>https://artifactory.intino.io/artifactory/release-libraries</url>
  </repository>
</repositories>
```

Para un Artifactory privado, use ese mismo `id` en `~/.m2/settings.xml` dentro de `<servers>` con sus credenciales. El plugin usa los repositorios y credenciales de Maven al resolver cada coordenada `groupId:artifactId:version`.

Konos no acepta un directorio como argumento: acepta un fichero de argumentos. Si no se proporciona `argumentsFile`, el plugin crea `target/intino/export.args` o `target/intino/build.args` con los directorios Maven convencionales. Aporte un fichero propio cuando el DSL requiera propiedades específicas (`dsl`, `level`, dependencias, etc.).

Por defecto, el código de Konos se genera en `gen`. Durante `import`, el plugin conserva el valor de `Builder(generationPackage="...")` y las declaraciones `Parameter("...")` del `.legio`. Konos genera en el paquete del artefacto más ese sufijo: por ejemplo, `com.example.catalogo` y `generationPackage="box"` producen `com.example.catalogo.box`. Si no existe ese ajuste, el sufijo por defecto es `konos`. Se pueden sobrescribir con `generatedSourcesDirectory`, `generationPackage` y `moduleParameters` (lista separada por `;`).

El plugin localiza la raíz del proyecto Intino subiendo desde el módulo hasta encontrar `.intino` o `project.legio`; pasa `<raíz>/.intino` a Konos mediante `intino.project.path`, de modo que la caché queda por módulo en `.intino/Konos/<artifactId>` y nunca deja un directorio `konos` dentro del módulo. El importador toma el output del proyecto configurado por IntelliJ (`.idea/misc.xml`) o, si no existe, `<raíz>/out`, y genera el POM con `out/build/<módulo>`, `out/production/<módulo>` y `out/test/<módulo>`. Las clases compiladas se escriben en ese `outputDirectory`; no se fuerza `target/classes`.

## IntelliJ IDEA

Para que **Build** o **Rebuild** ejecute también los goals de este plugin, configure IntelliJ para delegar la compilación en Maven:

1. Abra `Settings` → `Build, Execution, Deployment` → `Build Tools` → `Maven` → `Runner`.
2. Active **Delegate IDE build/run actions to Maven**.
3. Recargue el proyecto Maven después de cambiar el `pom.xml`.

Con esa opción activa, al reconstruir el módulo IntelliJ invoca Maven. La fase `compile` ejecuta previamente `initialize`, `generate-sources` y `process-sources`, por lo que lanza `intino:import`, `intino:export` e `intino:generate` antes de compilar Java. Para ejecutar solo la importación desde la terminal, use `mvn initialize`.

Los goals directos aparecen en la ventana **Maven**, dentro de **Plugins > intino**: `import`, `export`, `compile`, `compile-ui`, `package`, `release` y `resolve-web-artifacts`. Si el módulo se creó después de abrir IntelliJ, haga clic derecho sobre su `pom.xml` y elija **Add as Maven Project**; después use **Reload All Maven Projects**. Entonces podrá lanzar `intino:compile`, `intino:compile-ui`, `intino:package` o `intino:release` con doble clic desde esa vista.

Para actualizar únicamente el código generado de los paquetes web, ejecute `mvn intino:compile-ui`. Invoca Konos con `compilation.mode=OnlyElements`, sin compilar Java ni ejecutar npm. Konos resuelve cada `Service InventarioElements as UI` en el directorio hermano `../inventario-elements`.
