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
| `intino:release-ui` | — (manual) | Empaqueta y publica únicamente los clientes UI en el repositorio configurado en el servidor. |
| `intino:validate` | — (manual) | Comprueba el módulo servidor, sus modelos y los clientes UI declarados antes de compilar. |
| `intino:init` | — (manual) | Crea un POM agregador para módulos servidores Maven ya existentes. |

`initialize` es la fase más temprana útil. Para migrar un proyecto con varios módulos, ejecute primero `mvn io.intino:intino-maven-plugin:1.0.0:import` desde cada módulo (o con `-pl`); Maven ya ha leído los POM del proyecto antes de ejecutar cualquier fase, por lo que los POM recién creados no pueden incorporarse a esa misma ejecución.

## Varios módulos servidores

### Crear los POM iniciales con un script

Desde la raíz de su proyecto puede descargar el script de GitHub y ejecutarlo sin clonar este repositorio:

```bash
curl -fL https://raw.githubusercontent.com/intino/maven-plugin/master/scripts/intino-init.sh -o intino-init.sh && bash intino-init.sh
```

El comando descarga `intino-init.sh` en el directorio actual y prepara los POM de ese proyecto. Para indicar otra carpeta, use `bash intino-init.sh /ruta/a/mi-proyecto`. También puede ejecutar el script desde una copia local del repositorio:

```bash
bash /ruta/a/intino-maven-plugin/scripts/intino-init.sh /ruta/a/mi-proyecto
```

También puede copiar `scripts/intino-init.sh` al proyecto y ejecutarlo allí con `bash intino-init.sh`. Solo crea los POM iniciales: no ejecuta `import`, npm, compilación ni publicación. Conserva los POM existentes. Si falta el POM raíz, detecta los módulos servidores hijos; si existe, usa su lista `<modules>`. Los clientes con `WebImports` no se incluyen como módulos Maven.

El script configura automáticamente el repositorio de plugins mediante un POM temporal y usa `intino:init` para leer la versión de Konos de cada `artifact.legio`. La versión predeterminada del plugin es `1.0.0`; puede elegir otra con `INTINO_PLUGIN_VERSION=1.0.1 bash intino-init.sh`. Esa versión debe estar publicada con el goal `init` o instalada en su repositorio Maven local. Requiere Bash, Maven y Java.

### Configuración manual alternativa

`intino:import` no es agregador: se ejecuta una vez por cada módulo Maven servidor. Solo procesa el `artifact.legio` situado en el directorio de ese módulo y los clientes hermanos declarados por sus `Service ... as UI`; nunca recorre los módulos hijos del POM raíz.

Los proyectos con `packaging=pom` se omiten en `intino:import`, sin exigir versiones de Konos ni de los conversores en el agregador. Puede ejecutar `mvn intino:import` desde la raíz para importar los servidores del proyecto con varios módulos; la raíz se omite automáticamente.

Cree este `pom.xml` en la raíz del proyecto, junto a `project.legio`, y adapte las coordenadas y los nombres de los módulos. Declare solo los servidores en `<modules>`; los clientes se localizan como directorios hermanos de cada servidor. Un servidor sin parte cliente también puede incluirse.

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.monentia.cardio</groupId>
  <artifactId>marco-referencia</artifactId>
  <version>1.0.0</version>
  <packaging>pom</packaging>
  <modules>
    <module>inventario</module>
    <module>facturacion</module>
  </modules>
  <pluginRepositories>
    <pluginRepository>
      <id>intino-maven</id>
      <url>https://artifactory.intino.io/artifactory/release-libraries</url>
    </pluginRepository>
  </pluginRepositories>
  <build>
    <plugins>
      <plugin>
        <groupId>io.intino</groupId>
        <artifactId>intino-maven-plugin</artifactId>
        <version>1.0.0</version>
        <inherited>false</inherited>
      </plugin>
    </plugins>
  </build>
</project>
```

La declaración del plugin en la raíz permite usar el prefijo `intino` desde ese directorio. `pluginRepositories` indica dónde descargarlo. Las declaraciones de los módulos servidores se generan por separado; no añada ejecuciones de generación al POM agregador.

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

Tras crear o cambiar los POM durante una migración, ejecute `mvn -pl :inventario,:facturacion intino:import` una vez para regenerarlos y después recargue el proyecto con varios módulos en IntelliJ.

Si los POM de los módulos ya existen y falta únicamente el agregador en la raíz, puede crearlo sin POM previo mediante la coordenada completa del plugin:

```bash
mvn io.intino:intino-maven-plugin:1.0.0:init
```

Si no existe POM raíz, detecta los servidores hijos con `artifact.legio` y crea el agregador y los POM iniciales que falten. Si ya existe un POM raíz con `<modules>`, lee esa lista y conserva el agregador y todos los POM existentes. Excluye los artefactos cliente con `WebImports`.

Cada POM inicial incluye `repositories`, `pluginRepositories` y la versión del plugin que ejecuta `init`. La versión del builder se obtiene del `Dsl(name="Konos", version="...")` del `artifact.legio` de cada módulo. Si falta esa declaración, informa del error sin inventar una versión. Las versiones iniciales de los conversores se pueden ajustar con `-Dintino.legio2mavenVersion=...` y `-Dintino.legio2packagejsonVersion=...`.

Si el POM raíz declara módulos cuyos POM aún no existen, inicialice sin recorrer los módulos del proyecto y después mígrelos:

```bash
mvn -N intino:init
mvn -pl :inventario,:facturacion intino:import
```

`-N` (`--non-recursive`) hace que Maven cargue únicamente el proyecto raíz. Es necesario en esta primera inicialización porque, sin él, Maven intentaría cargar los POM de los módulos antes de ejecutar `init` y fallaría si todavía no existen. `init` lee la lista `<modules>` por su cuenta y crea los POM que faltan.

Una vez creados esos POM, deje de usar `-N` para importar, compilar o empaquetar los módulos: de lo contrario Maven ejecutaría únicamente el proyecto raíz. Use `-pl` para seleccionar servidores o el ciclo Maven sin selectores para procesar todo el proyecto con varios módulos:

```bash
mvn -pl :inventario,:facturacion intino:import
mvn -pl :inventario intino:compile
mvn package
```

Si aparece `No plugin found for prefix 'intino'`, compruebe que el POM raíz contiene tanto la declaración del plugin como `pluginRepositories`, tal y como muestra el ejemplo. También puede invocar `mvn -N io.intino:intino-maven-plugin:1.0.0:init` para evitar la búsqueda por prefijo; esta alternativa sigue necesitando que el plugin esté instalado localmente o disponible en un repositorio de plugins configurado.

Los POM iniciales sirven para ejecutar `import`; las coordenadas y la configuración definitivas se generan desde Legio en la importación. Para usar `init` sin ningún POM, el plugin debe estar instalado localmente o disponible mediante un repositorio de plugins configurado en `settings.xml`.

## Uso

Maven resuelve los plugins en `pluginRepositories`, que es distinto de `repositories`. Declare el repositorio de plugins de Intino antes de usar el prefijo `intino`; si no se declara, `mvn intino:...` no puede encontrar el plugin aunque `release-libraries` esté en `repositories`. El POM generado por `intino:import` añade este repositorio automáticamente, pero el POM temporal de la primera migración debe declararlo para poder resolver el plugin por primera vez.

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
      <id>intino-maven</id>
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

Instale antes el plugin localmente (`cd intino-maven-plugin && mvn install`) o publique una versión no-SNAPSHOT en su repositorio Maven. Después ejecute `mvn intino:import`. El conversor reemplaza el POM temporal por el POM migrado, conserva cada `.legio` y mantiene en el POM generado la misma versión de `intino-maven-plugin` indicada en la primera migración. Es la fuente de verdad y en cada importación se regenera el `pom.xml` o el `package.json`. Revise los cambios en Git antes de continuar con `mvn compile`.

## Distribuir el plugin

### Distribuir las partes cliente

Desde el módulo servidor puede empaquetar y distribuir todos sus clientes UI sin indicar rutas:

```bash
mvn intino:release-ui
```

Desde la raíz de un proyecto con varios módulos, seleccione el servidor:

```bash
mvn -pl :inventario intino:release-ui
```

El goal detecta los `Service ... as UI`, ejecuta el build npm solo si es necesario, genera los JAR principal y `-sources` y sus POM, y los publica mediante Maven. Usa el `distributionManagement` del servidor y las credenciales de `settings.xml`; conserva los archivos de settings personalizados (`-s`, `-gs`) y el repositorio local de la sesión. Las versiones `-SNAPSHOT` usan el repositorio de snapshots según las reglas de Maven. No compila Java ni publica el artefacto servidor. En el POM agregador se omite automáticamente. Si varios clientes se publican y falla uno posterior, las publicaciones anteriores no se revierten.

Requiere `mvn` en PATH; puede configurar otro ejecutable con `-Dintino.mavenExecutable=/ruta/a/mvn`. El goal también aparece en **Maven > Plugins > intino > release-ui** en IntelliJ tras recargar el proyecto.

Al empaquetar un servidor, `intino:package-ui` también prepara cada cliente declarado como `Service ... as UI` como artefacto Maven independiente. Sus coordenadas (`groupId`, nombre y versión) se leen de su propio `artifact.legio`.

En `${project.build.directory}/intino/clients/<cliente>/` se genera un `pom.xml` y, dentro de `target/`, `<artifactId>-<version>.jar` y `<artifactId>-<version>-sources.jar`. Ambos JAR contienen únicamente `src/`, `gen/`, `res/`, `package.json` y los metadatos `META-INF/MANIFEST.MF` y `META-INF/maven/...`. No se genera Javadoc.

El POM cliente copia el `distributionManagement` del servidor. Para instalar o publicar el cliente por separado, utilice la ruta del POM que imprime el empaquetado:

```bash
mvn -f /ruta/al/build/intino/clients/inventario-elements/pom.xml install
mvn -f /ruta/al/build/intino/clients/inventario-elements/pom.xml deploy
```

Maven empaqueta y publica el JAR principal, el `-sources.jar` y el POM con las coordenadas del cliente. Las credenciales se configuran en `settings.xml` para el identificador del repositorio de distribución. El empaquetado del servidor prepara estos artefactos locales; `intino:release-ui` realiza su empaquetado y publicación en un único comando. Los comandos con `-f` siguen disponibles como alternativa manual.

### Publicar el propio plugin

El plugin usa el mecanismo estándar `distributionManagement` de Maven, por lo que puede publicarse en cualquier Artifactory o repositorio Maven compatible. Configure en el `pom.xml` el repositorio de releases y, si procede, el de snapshots; el identificador (`id`) debe coincidir con una entrada de `~/.m2/settings.xml` que contenga las credenciales.

El POM de este proyecto incluye Artifactory Intino como configuración inicial (`release-libraries` y `snapshot-libraries` bajo el servidor `intino-maven`), pero se puede sustituir por el Artifactory que corresponda. Desde el directorio del plugin, ejecute:

```bash
mvn intino:release
```

Para reconstruir desde cero antes de publicar, use `mvn clean intino:release`. El goal ejecuta las fases Maven hasta `deploy` para el proyecto Maven desde el que se invoca. Al usarlo en `intino-maven-plugin` publica el plugin; al usarlo en un módulo Intino migrado, publica ese módulo.

En los proyectos migrados, el goal interno `intino:package-ui` queda asociado a `prepare-package`. Antes de crear el JAR calcula una huella SHA-256 de las entradas relevantes de cada `Service ... as UI` (`src`, `gen`, `res`, `package.json`, `webpack.config.js` y WebArtifacts, excluyendo `node_modules`, `.git`, `target` y `out`) y solo ejecuta `npm run build` cuando esa huella cambia. Después copia el directorio resultante completo bajo `www` en el directorio de clases del servidor. Por ejemplo, un `output.path` acabado en `www/inventario-elements` se incorpora como `www/inventario-elements/...` al JAR. Puede adaptar el comando con `-Dintino.npmExecutable=pnpm` o `-Dintino.buildScript=build:production`; use `-Dintino.forceUiBuild=true` para forzar la regeneración.

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

Los goals directos aparecen en la ventana **Maven**, dentro de **Plugins > intino**: `import`, `export`, `compile`, `compile-ui`, `package`, `release`, `validate`, `init` y `resolve-web-artifacts`. Si el módulo se creó después de abrir IntelliJ, haga clic derecho sobre su `pom.xml` y elija **Add as Maven Project**; después use **Reload All Maven Projects**. Entonces podrá lanzar `intino:compile`, `intino:compile-ui`, `intino:package`, `intino:validate` o `intino:release` con doble clic desde esa vista.

Antes de una compilación o de una migración, ejecute `mvn intino:validate` para detectar un `artifact.legio`, directorio `src`, cliente UI, `package.json` o `webpack.config.js` ausente. También aparece como goal directo en IntelliJ.

Para actualizar únicamente el código generado de los paquetes web, ejecute `mvn intino:compile-ui`. Invoca Konos con `compilation.mode=OnlyElements`, sin compilar Java ni ejecutar npm. Konos resuelve cada `Service InventarioElements as UI` en el directorio hermano `../inventario-elements`.
