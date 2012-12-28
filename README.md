# Asynchroniczna obsługa wywołań http/https

## Case study: płatność internetowa wymagająca potwierdzenia na urządzeniu mobilnym

Tzw. agregatory płatności umożliwiają płacenie w Internet za pomocą różnych instrumentów płatniczych: płatność kartą, płatność przelewem, płatność z elektronicznej portmonetki. Płatności mobilne wprowadzają nowe możliwości, np. jednorazowe kody, wprowadzane na stronach akceptanta, które wymagają potwierdzenia przez użytkownika poprzez wprowadzenie kodu PIN na urządzeniu mobilnym.

W odróżnieniu od typowych płatności w Internet-e płatności mobilne wymagają natychmiastowej komunikacji nie tylko z systemami bankowymi ale również z urządzeniami mobilnymi. Użytkownik musi mieć czas na zaakceptowanie płatności. Całość przetwarzania realizowana jest w trybie synchronicznym (akceptant oczekuje na autoryzację).
W całym ciągu przetwarzania może wystąpić wiele wąskich gardeł, które przy zwiększonym wolumenie płatności wykonywanych w tym samym czasie, mogą doprowadzić do wysycenia liczby dostępnych wątków a co za tym idzie pojawienia się timeout-ów.

Podstawowym sposobem optymalizacji tego typu interakcji powinno być zastosowanie asynchronicznego przetwarzania. Od strony wywołującego usługę akceptacji płatności komunikacja w dalszym ciągu pozostaje synchroniczna (klient czeka na odpowiedź), natomiast przetwarzanie płatności odbywa się asynchronicznie. 

## Wykorzystanie servlet 3.0 z asynchronicznym trybem przetwarzania

Specyfikacja Java Servlet 3.0 wprowadziła możliwość asynchronicznego przetwarzania wywołań GET/POST poprzez protokół http/https. Klient wykonuje normalne wywołanie http/s, przekazuje dane do autoryzacji, czeka na odpowiedź. Servlet obsługujący wywołanie blokuje socket, tworzy asynchroniczny kontekst wywołania (AsyncContext) i przetwarza całość wywołania całkowicie asynchronicznie.

Takie podejście nie blokuje wątków kontenera servletów, blokuje jedynie socket-y tcp/ip.

## Struktura projektów

**coreservices-data** - podstawowe klasy (POJO) reprezentujące encje bankowe - w szczególności encję Payment. Wynikiem jest plik jar instalowany w lokalnym repozytorium maven.

**coreservices** - symulacja funkcjonalności głównych serwisów bankowych - w szczególności autoryzacji płatności z czasaem oczekiwania na akceptację płatności przez użytkownika (sztucznie generowane opóźnienie). Standardowy projekt Java, wykorzystujący framework *spring integration* do obsługi komunikatów oraz *activemq* jako broker komunikatów.

**paymentauthorization** - fasada (aplikacja web) przyjmująca wywołania http/s i przekazująca płatności do autoryzacji do *coreservices* jako komunikaty poprzez broker komunikatów *activemq* z wykorzystaniem *spring integration*.

## Utworzenie struktury projektów

Do budowy poszczególnych projektów wykorzystamy narzędzie *gradle*. W głównym folderze projektów tworzymy plik build.gradle z referencją do repozytorium z szablonami projektów (w stylu maven archetype).

Plik:
> build.gradle

Zawartość pliku:
> apply from: 'https://launchpadlibrarian.net/86359418/apply.groovy'

Następnie tworzymy 3 projekty:

    gradle createJavaProject    (Project name: coreservices-data)
    gradle createJavaProject    (Project name: coreservices)
    gradle createWebappProject  (Project name: paymentauthorization)

W głównym katalogu tworzymy plik:
> settings.gradle

z referencją do podprojektów, co umożliwi zarządzanie podprojektowami z katalogu głównego (np. gradle coreservices-data:install)
> include "coreservices-data", "coreservices", "paymentauthorization"

## coreservices-data

Projekt zawiera definicję podstawowych encji (klasy POJO) przekazywane pomiędzy projektami.
W szczególności encja Payment reprezentuje płatność z atrybutami: unikalnym identyfikatorem (w formie UUID), numerem rachunku (w formie NRB), kwotą płatności oraz wynikiem autoryzacji.

W build.gradle wskazujemy:

* zaleźność od plugin java
  
> apply plugin: 'java'

* wskazujemy repozytoria maven (lokalne oraz globalne)
  
  repositories {
    mavenLocal()
    mavenCentral()
  }

* wskazujemy zaleźności z bibliotekami (zależności na etapie kompilacji przenoszą się na etapu uruchomienia). Dla ułatwienia wykorzystujemy projekt lombok, pozwalający na wykorzystanie annotacji do automatycznego wygenerowani metod get/set/constructor.

	dependencies {
	  compile(
	    // PROJECT LOMBOK FOR FINE GETTERS AND SETTERS
	    [group: "org.projectlombok", name: "lombok", version: "0.11.4"],
	    // LOGGING CLASSES
	    [group: "org.slf4j", name: "slf4j-api", version: "1.6.4"],
	    [group: "ch.qos.logback", name: "logback-classic", version: "1.0.0"],
	  )
	}

* wskazujemy, gdzie po wywołaniu gradle install ma być umieszczony wynikowy plik jar

    uploadArchives {
      repositories {
        mavenDeployer {
          repository(url: "file:///${userHome}/.m2/repository")
        }
      }
    }
    uploadArchives.dependsOn "jar"

Encja płatności zdefiniowana została w pliku Payment.java (main/java/src/org/be3form/coreservices/data). Płatność przekazana do autoryzacji powinna zawierać atrybuty id, amount (kwota transakcji) oraz authorizationCode (sekretny kod z urządzenia mobilnego). Jako wynik autoryzacji uzupełnione zostaną atrybuty authorizationResult (wynik autoryzacji) oraz numer rachunku w przypadku autoryzacji pozytywnej.

    @ToString(includeFieldNames=true)
    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public class Payment implements Serializable {
      // Possible authorization results
      public enum PAYAUTH_RESULT {
        AUTHORIZED,
        REJECTED
      }
      // Unique identifier of the payment
      @Getter @Setter private String id;
      // Amount to be authorized
      @Getter @Setter private double amount;
      // Authorization code - secret code
      @Getter @Setter private String authorizationCode;

      // Authorization code - secret code
      @Getter @Setter private PAYAUTH_RESULT authorizationResult;
      // Account number in NRB form - as result of account assiged with authorizationCode
      @Getter @Setter private String accountNumber;
    }

## coreservices

Projekt jest z jednej strony brokerem integracyjnym (udostępnia mechanizm kolejek poprzez *activemq*) jak i zaślepioną implementacją usługi walidacji płatności symulującą oczekiwania na reakcję użytkownika. Serwis autoryzacyjny uruchamiany jest zdarzeniowo (message driven), w chwili pojawienia się komunikatu w kolejce autoryzacyjnej. Liczba równolegle przetwarzanych autoryzacji uzależniona jest od ustawień maksymalnej liczby konsumentów komunikatów (maxConcurrentCosumers).

Podobnie jak w projekcie *coreservices-data*, do pliku *build.gradle*, dodajemy zależności do bibliotek zewnętrznych:

    dependencies {
      compile(
        [group: "org.apache.activemq", name: "activemq-core", version: "5.6.0"],
        
        // SCHEMA DRIVEN PROPRIETARY NAMESPACE HANDLER
        [group: "org.apache.xbean", name: "xbean-spring", version: "3.11.1"],
        
        // SPRING INTEGRATION
        [group: "org.springframework.integration", name: "spring-integration-jms", version: "2.2.0.RELEASE"],
        
        // CORESERVICES DATA BEANS
        [group: "org.be3form.coreservices", name: "coreservices-data", version: "0.1"],

        // LOGGING CLASSES
        [group: "org.slf4j", name: "slf4j-api", version: "1.6.4"],
        [group: "ch.qos.logback", name: "logback-classic", version: "1.0.0"],
      )
    }

Projekt zależy od biblioteki activemq jako brokera komunikatów oraz spring integration jako mechanizmu łatwej intgracji z kolejkami poprzez protokół JMS.

Ponieważ projekt jest projektem java, to definiujemy zadanie gradle do uruchomienia serwera:

    task runApp(type: JavaExec, dependsOn: classes) {
      main = "org.be3form.coreservices.CoreServicesApp"
      classpath = sourceSets.main.runtimeClasspath
      // by default gradle sets standard input to empty stdin for child processes
      standardInput = System.in
    }

Zadanie to wskazuje klasę *CoreServicesApp* jako klasę serwera (src/main/java/org/be3form/coreservices)

    public class CoreServicesApp {
      public static void main (String[] args) throws InterruptedException, IOException {
        System.out.println("===== STARTED =====");
        System.out.println("Enter 'c' in order to stop APP");

        AbstractApplicationContext context = new ClassPathXmlApplicationContext("/META-INF/org/be3form/coreservices/amq-config.xml");

        // hang and wait for user action
        try {
          char c = 0;
          while (c != 'c') {
            c = (char)System.in.read();
          }
        }
        catch (Exception ex) {
          // Ignore terminations
        }
        finally {
          System.out.println("===== FINISHED =====");
          context.close();
        }
      }
    }

W klasie serwera pobieramy kontekst spring framework z definicją brokera, kolejek, aktywatorów i gateway do asynchronicznego przetwarzania. 
Konfiguracja spring-owa umieszczona została w pliku amg-config.xml (src/resources/META-INF/org/be3form/coreservices). 

Najważniejsze elementy konfiguracji to definicja brokera activemq, fabryki połączeń (nazwa connectionFactory jest domyślną nazwą, która jest automatycznie wykorzystywana przez elementy spring integration).

    <amq:broker brokerName="coreservices" useJmx="true" persistent="false">
      <amq:transportConnectors>
        <amq:transportConnector name="openwire" uri="tcp://localhost:61616"/>
      </amq:transportConnectors>
    </amq:broker>

    <!-- define connection factory. The name is important: connectionFactory could be resolved automatically by channel definitions -->
    <bean id="connectionFactory" class="org.springframework.jms.connection.CachingConnectionFactory">
      <property name="targetConnectionFactory">
        <bean class="org.apache.activemq.ActiveMQConnectionFactory">
          <property name="brokerURL" value="tcp://localhost:61616"/>
        </bean>
      </property>
      <property name="sessionCacheSize" value="10"/>
      <property name="cacheProducers" value="false"/>
    </bean>

Pozostałe elementy konfiguracji to definicja kolejki (w serwerze activemq), adaptera zorientowanego na zdarzenia/komunikaty JMS, kanału (w konwencji spring integration), do którego przekierowywane są płatności podlegające autoryzacji oraz serwisu do obsługi płatności:

    <bean id="payAuthQueue" class="org.apache.activemq.command.ActiveMQQueue">
      <constructor-arg value="PAY_AUTH_REQ_Q?consumer.prefetchSize=0"/>
    </bean>
    <!-- message driven channel addapter -->
    <jms:message-driven-channel-adapter id="jmsPayAuthChannel" destination="payAuthQueue" channel="payAuthChannel" 
                                        concurrent-consumers="1"
                                        max-concurrent-consumers="100"
                                        />
    <!-- define channel with jms backend and pooling settings (this gives us controll over how many separate instances handle messages) -->
    <int:channel id="payAuthChannel"/>
    <!-- define service activator for payment authorization -->
    <int:service-activator id="payAuthActivator" input-channel="payAuthChannel" ref="paymentAuthorizationService" method="authorize"/>

Ważnym ustawieniem jest atrybut consumer.prefetchSize=0 umieszczony w definicji kolejki, który oznacza, że serwis przetwarzający komunikaty nie będzie dostawał więcej niż jednego komunikatu na raz. Ponieważ przetwarzanie płatności może być dugotrwałe ważne jest maksymalne wykorzystanie wszystkich wątków dostępnych w coreservices.

Odpowiedzi, przekazywane są asychronicznie do kolejki odpowiedzi:

    <!-- outbound queue definition -->
    <bean id="payAuthResponseQueue" class="org.apache.activemq.command.ActiveMQQueue">
      <constructor-arg value="PAY_AUTH_RSP_Q"/>
    </bean>

    <jms:outbound-channel-adapter id="jmsPayAuthResponseChannel" destination="payAuthResponseQueue" channel="payAuthResponseChannel"/>

    <int:channel id="payAuthResponseChannel" />

    <int:gateway id="paymentAuthorizationResponseGateway" 
        service-interface="org.be3form.coreservices.service.PaymentAuthorizationResponseGateway" 
        default-request-channel="payAuthResponseChannel" />

Komunikaty przetwarzane są poprzez serwis *PaymentAuthorizationService* (src/main/java/org/be3form/coreservices/service/impl).
Odpowiedzi przekazywane są do kolejki odpowiedzi za pomocą PaymentAuthorizationResponseGateway (src/main/java/org/be3form/coreservices/service), mechanizmu spring integration ułatwiającego komunikację z kolejkami.

## paymentauthorization

Projekt jest aplikacją web udostępniającą serwis pod adresem *http://localhost:8080/paymentauthorization/paymentauth* z wykorzystaniem servlet 3.0. Ta wersja servlet-ów umożliwa przetwarzanie asynchroniczne.

W pliku *build.gradle* definiujemy zależności z wykorzystywanymi bibliotekami (etap compile stosuje się również do części runtime).

    repositories {
      mavenLocal()
      mavenCentral()
    }
    
    dependencies {
      compile (
        [group: 'org.springframework', name: 'spring-web', version: '3.1.2.RELEASE'],

        // CORESERVICES DATA BEANS
        [group: "org.be3form.coreservices", name: "coreservices-data", version: "0.1"],
        // THIS APPLICATION IS ACTIVEMQ CLIENT
        [group: "org.apache.activemq", name: "activemq-core", version: "5.6.0"],
        // ADDITIONAL XSD SCHEMA NAMESPACE HANDLER
        [group: "org.apache.xbean", name: "xbean-spring", version: "3.11.1"],
        // SPRING INTEGRATION
        [group: "org.springframework.integration", name: "spring-integration-jms", version: "2.2.0.RELEASE"],

        // LOGGING CLASSES
        [group: "org.slf4j", name: "slf4j-api", version: "1.6.4"],
        [group: "ch.qos.logback", name: "logback-classic", version: "1.0.0"],
      )

W pliku web.xml umieszczonym w katalogu src/main/webapp/WEB-INF/web.xml dodajemy konfigurację spring context z definicją bean-ów (spring-owych) w pliku application-context.xml

    <context-param>
      <param-name>contextConfigLocation</param-name>
      <param-value>/WEB-INF/application-context.xml</param-value>
    </context-param>
    
    <listener>
      <listener-class>
        org.springframework.web.context.ContextLoaderListener
      </listener-class>
    </listener>

Gradle (w wersji 1.2 oraz 1.3) obsługuje jedynie wersję 6 serwera web-owego jetty (plugin jettyRun). Do poprawnego działania wersji 3.0 servlet-ów konieczna jest wersja 7+. By uruchomić taką wersję z poziomu gradle konieczne jest wprowadzenie: nowej konfiguracji:

    configuration {
      jetty8
    }

w sekcji dependencies wykorzystujemy referencję do jetty-runner:

    jetty8 (
        [group: 'org.mortbay.jetty', name: 'jetty-runner', version: '8.1.5.v20120716'],
        // LOGGING CLASSES
        [group: "org.slf4j", name: "slf4j-api", version: "1.6.4"],
        [group: "ch.qos.logback", name: "logback-classic", version: "1.0.0"],
      )

Definiujemy nowe zadanie:

    task jetty8Run(type: JavaExec, dependsOn: assemble) {
        main = "org.mortbay.jetty.runner.Runner"
        args = ["--path", war.baseName, war.archivePath]
        classpath configurations.jetty8
    }

Do utworzenia servlet-u przyjmującego ruch autoryzacji płatności wykorzystamy fraemwork spring.
W pliku build.gradle w sekcji dependencies dodajemy zależność:

    compile(
          [group: 'org.springframework', name: 'spring-web', version: '3.1.2.RELEASE'],
    )

Do kompilacji funkcjonalności servlet 3.0 dodajemy bibliotekę servlet-api. Wykorzystujemy zadanie providedCompile do wskazania, że biblioteka ta jest potrzebna wyłącznie na etapie kompilacji.

    dependencies {
      providedCompile (
        [group: 'javax.servlet', name: 'javax.servlet-api', version: '3.0.1'],
      )
    }

Tworzymy nowy plik z definicją servletu PaymentAuthorizationHttpServlet (src/main/java/org/be3form/web/servlet) z wykorzystaniem annotacji (z servlet 3.0) WebServlet. Dzięki tej deklaracji nie musimy umieszczać konfiguracji w pliku web.xml.

    @WebServlet(
      description="Payment authorization http servlet", 
      urlPatterns= {"/paymentauth"}, 
      name="paymentAuthorizationHttpServletHandler",
      asyncSupported=true)
    public class PaymentAuthorizationHttpServlet extends HttpRequestHandlerServlet {

    }

W deklaracji wskazujemy ścieżkę (uri), pod jaką dostępny będzie servlet. Jednocześnie deklarujemy, że servlet będzie obsługiwał asynchroniczne wywołania.

Zgodnie z spring-web wydzielamy implementację wywołań get oraz post do oddzielnej klasy PaymentAuthorizationHttpServletHandler (src/main/java/org/be3form/web/servlet).

    @Service("paymentAuthorizationHttpServletHandler")
    public class PaymentAuthorizationHttpServletHandler implements HttpRequestHandler {
      ...
    }

Klasa posiada referencję (automatycznie tworzoną poprzez annotację @Autowired) do PaymentAuthorizationService - klasy wysyłającej komunikat autoryzacyjny. 

Najważniejsza w implementacji jest metoda handleRequest, inicjująca asynchroniczne przetwarzanie w servlet poprzez utworzenie AsyncContext, wystartowanie (poprzez paymentExecutor) krótkiego przetwarzania płatności z ustawioną wartością timeout na całość przetwarzania.

    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      // increment request counter
      long requestId = this.requestCounter.incrementAndGet();
      
      log.debug("===== START ASYNC TEST: [" + Long.toString(requestId) + "] =====");

      // start async context for the servlet
      AsyncContext actx = request.startAsync(request, response);
      // set timeout for servlet's request
      actx.setTimeout(50000);
      
      PaymentProxy paymentProxy = new PaymentProxy(actx, paymentAuthorizationService);
      actx.addListener(paymentProxy);

      paymentExecutor.submit(paymentProxy);
      
      log.debug("===== SERVLET THREAD PROCESSING [" + Long.toString(requestId) + "] =====");
    }

Metoda tworzy instancję PaymentProxy, implementującej interfejs Callable<Void> z metodą:

    public Void call() throws Exception {
      // create payment and send it to the coreservice backend for authorization
      log.debug("*** Payment authorization created! ***");

      // generate unique request ID
      UUID uuid = UUID.randomUUID();
      // TODO: create example payment
      Payment payment = new Payment(uuid.toString(), 100.0, "123456", null, null);
      // send payment for authorization to coreservices
      paymentAuthorizationService.sendPaymentAuthorizationRequest(payment, actx);
            
      return null;
    }

Metoda ta wysyła komunikat JMS poprzez paymentAuthorizationService do brokera *activemq*.

W pliku (src/main/webapp/WEB-INF) application-context.xml definiujemy 

* pulę wątków do asynchronicznego przetwarzania wywołań http - widać, że do efektywnego przetawrzania asynchronicznego wystarcza bardzo mała liczba wątków.

    <!-- define spring thread pool task executor -->
    <task:executor id="paymentExecutor" pool-size="2" rejection-policy="CALLER_RUNS" />

* fabrykę połączeń do brokera komunikatów *activemq*

  <bean id="connectionFactory" class="org.springframework.jms.connection.CachingConnectionFactory">
    <property name="targetConnectionFactory">
      <bean class="org.apache.activemq.ActiveMQConnectionFactory">
        <property name="brokerURL" value="tcp://localhost:61616"/>
      </bean>
    </property>
    <property name="sessionCacheSize" value="10"/>
    <property name="cacheProducers" value="false"/>
  </bean>

* kolejkę do przekazania płatności do autoryzacji wraz z kanałem (w terminologii spring integration)

    <!-- request queue -->
    <bean id="payAuthRequestQueue" class="org.apache.activemq.command.ActiveMQQueue">
      <constructor-arg value="PAY_AUTH_REQ_Q"/>
    </bean>

    <jms:outbound-channel-adapter id="jmsPayAuthRequestChannel" destination="payAuthRequestQueue" channel="payAuthChannel"/>

    <int:channel id="payAuthChannel" />

    <int:gateway id="paymentAuthorizationGateway" 
        service-interface="org.be3form.service.PaymentAuthorizationGateway" 
        default-request-channel="payAuthChannel" />

* kolejkę odpowiedzi, wraz z kanałem (w terminologii spring ingration) oraz serwisem obsługującym asynchroniczne odpowiedzi

    <!-- response queue -->
    <bean id="payAuthResponseQueue" class="org.apache.activemq.command.ActiveMQQueue">
      <constructor-arg value="PAY_AUTH_RSP_Q"/>
    </bean>

    <jms:message-driven-channel-adapter id="jmsPayAuthResponseChannel" destination="payAuthResponseQueue" channel="payAuthResponseChannel" />

    <int:channel id="payAuthResponseChannel"/>
    
    <int:service-activator input-channel="payAuthResponseChannel" ref="paymentAuthorizationService" method="handlePaymentAuthorizationResponse" />

Serwis PaymentAuthorizationService/Impl definiuje 2 metody: sendPaymentAuthorizationRequest oraz handlePaymentAuthorizationResponse. Ponieważ serwis spring-owy z definicji jest singletonem to wewnątrz klasy definiujemy hashmap-ę z przechowującą identyfikatowy płatności z referencją do asynchronicznego kontekstu servletu (dzięki temu istnieje możliwość pobrania referencji do HttpResponse i przekazania odpowiedzi).

## Uruchomienie i testy

Budujemy i uruchmiamy projekty w nastepującej kolejności:

* coreservices-data: gradle install
* coreservices: gradle RunApp
* paymentauthorization: gradle jetty8Run

Funkcjonalność możemy przetestować za pomocą narzędzia siege (jest to o tyle dobre narzędzie, że symuluje tworzenie osobnych sesji użytkowników).

> siege -c100 -r2 -b http://localhost:8080/paymentauthorization/paymentauth

Powyższe wywołanie symuluje 100 równoległych klientów (różne połączenia http) wykonywanych 2 krotnie.

## Skalowalność

Skalowalność rozwiązania możemy zwiększyć poprzez ustawienie ilości równoległych wątków (max-concurrent-consumers) w pliku coreservices/src/main/resources/META-INF/org/be3form/coreservices/amq-config.xml.

    <jms:message-driven-channel-adapter id="jmsPayAuthChannel" destination="payAuthQueue" channel="payAuthChannel" 
                                        concurrent-consumers="1"
                                        max-concurrent-consumers="100"
                                        />
