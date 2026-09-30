# PartiMo

App Android nativa (Kotlin) per organizzare viaggi in modo intelligente in qualunque città del mondo.
All'avvio chiede "Dove vuoi andare?": si cerca una città oppure si tocca **Consigliami** per ricevere
le mete più adatte al periodo scelto. Il **punto di partenza** lo decide l'utente (città e aeroporto)
e il periodo può essere "Prossimi giorni" o **uno qualunque dei prossimi dodici mesi**.

La meta scelta apre una dashboard divisa in sezioni, ognuna con il suo pulsante nella barra in basso:
**Voli**, **Alloggi**, **Da vedere**, **Trasporti**, **Ristoranti**. Il pulsante **Aggiorna** cerca le
offerte last minute ignorando la cache. La **campanella** attiva gli avvisi: PartiMo controlla i prezzi
in background e manda una notifica quando voli o alloggi diventano davvero convenienti.

<img src="docs/search_ideas.png" alt="Schermata iniziale con partenza, mesi e consigli" width="240" /> <img src="docs/departure_picker.png" alt="Scelta dell'aeroporto di partenza" width="240" /> <img src="docs/trip_dashboard.png" alt="Dashboard: sezione Voli" width="240" /> <img src="docs/trip_dashboard_explore.png" alt="Dashboard: sezione Da vedere" width="240" />

## Stack

| Area | Tecnologia |
|---|---|
| Linguaggio | Kotlin 2.3.21 (Kotlin integrato in AGP 9) |
| UI | Jetpack Compose (BOM 2026.06.01) + Material 3 |
| Architettura | Clean Architecture a moduli (`:domain`, `:data`, `:app`) + MVVM |
| Asincronia | Coroutines, `StateFlow` / `Flow` |
| Rete | Ktor Client 3.5 (motore OkHttp) + kotlinx.serialization |
| Cache locale | Room 2.8 (KSP) |
| Preferenze | DataStore Preferences 1.2 (punto di partenza, viaggi seguiti) |
| Lavori in background | WorkManager 2.12 (controllo periodico delle offerte) + notifiche |
| Immagini | Coil 3 |
| Build | Gradle 9.5.1, AGP 9.3.3, version catalog, `compileSdk`/`targetSdk` 36, `minSdk` 26 |
| Navigazione | Navigation Compose 2.9 con rotte type-safe (`@Serializable`) |
| Test | JUnit 4 + kotlin.test, kotlinx-coroutines-test, Turbine, Ktor MockEngine, Robolectric + Compose UI test |

## Architettura

```
┌─────────────────────────────── :app (presentation) ────────────────────────────────┐
│ NavHost: Search ("Dove vuoi andare?") → Dashboard (5 sezioni) · Scelta partenza     │
│            ▲ StateFlow<…UiState> (schermate stateless)                              │
│ SearchVM · TripDashboardVM · DeparturePickerVM ── AppContainer (DI manuale)         │
│ DealCheckWorker (WorkManager) → DealNotifier (notifiche, apertura del viaggio)      │
└──────────────────────────────────────┬──────────────────────────────────────────────┘
                                       │ casi d'uso
┌─────────────────────────────── :domain (Kotlin puro) ──────────────────────────────┐
│ UseCase → servizi (qualità/prezzo, stagionalità, tag foto, aeroporti, affari)        │
│        → interfacce Repository · modelli (TravelPeriod, PriceWatch…) · DataResult    │
└──────────────────────────────────────▲──────────────────────────────────────────────┘
                                       │ implementa
┌───────────────────────────── :data (Android library) ──────────────────────────────┐
│ DefaultXxxRepository (safeApiCall: eccezioni → DataError, Dispatchers.IO)            │
│   → DataSource: provider reale (Ktor + ResponseCache/Room) oppure demo               │
│ DataStore: preferenze e viaggi seguiti · DataModule: sceglie reale/demo              │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

- **`:domain`** è un modulo JVM puro: nessuna dipendenza da Android, rete o database. Contiene i
  modelli, le interfacce dei repository, i casi d'uso e la logica di business testabile.
- **`:data`** implementa i repository. Ogni provider è dietro un'interfaccia `DataSource`, quindi
  cambiare provider (ad esempio passare da un'API al proprio backend) richiede una sola classe.
- **`:app`** contiene Compose, i ViewModel, le notifiche e la *composition root*: l'unico punto che
  conosce la configurazione (`BuildConfig`).
- Gli errori non attraversano i layer come eccezioni: i repository restituiscono
  `DataResult.Success(data, origin)` oppure `DataResult.Failure(DataError)`. La UI li traduce in
  `UiState` `Loading` / `Success` / `Empty` / `Error`.

## Funzionalità

| Modulo | Dominio | Provider reale | Note |
|---|---|---|---|
| Ricerca mete nel mondo | `SearchCitiesUseCase`, `ResolveDestinationUseCase`, `AirportSelector` | [Open-Meteo Geocoding](https://open-meteo.com/en/docs/geocoding-api) (gratuito, senza chiave) + dataset aeroporti [OurAirports](https://ourairports.com/data/) | Ricerca con debounce e nomi in italiano; scelta automatica dell'aeroporto di arrivo (hub internazionale > grande > più vicino) |
| Punto di partenza | `FindDepartureAirportsUseCase`, `ObserveDepartureUseCase`, `SaveDepartureUseCase` | Geocoding + aeroporti, salvato con DataStore | L'utente cerca la sua città e sceglie l'aeroporto: il consigliato è in cima, ma può preferirne un altro (es. Linate invece di Malpensa). Cambiandolo, i voli si ricaricano da soli |
| Periodo del viaggio | `TravelPeriod` | – | "Prossimi giorni" + i dodici mesi successivi: ogni mese dell'anno è selezionabile, sia nella ricerca sia nella dashboard |
| Consigliami | `RecommendDestinationsUseCase` | Catalogo curato di ~50 mete + meteo attuale | Mete adatte al mese del viaggio: mercatini, aurora boreale, fioriture, foliage, mare, clima ideale |
| Voli e alloggi | `SearchFlightsUseCase`, `SearchAccommodationsUseCase`, `ValueForMoneyScorer` | [Duffel](https://duffel.com/docs) (offer requests, Stays) | Punteggio qualità/prezzo normalizzato; media bayesiana sulle recensioni; filtri e ordinamenti |
| Aggiorna (last minute) | `TripDashboardViewModel.refresh`, `PriceChange` | – | Ricarica tutto ignorando la cache e riassume la variazione dei prezzi migliori (es. "🔥 Volo sceso a 89 € (−12 €)") |
| Avvisi sulle offerte | `SetPriceAlertUseCase`, `CheckPriceWatchesUseCase`, `DealDetector` | WorkManager + notifiche | Controllo ogni 6 ore con rete disponibile; notifica se volo o alloggio (ben recensito) costa almeno il 15% in meno del solito; tocco sulla notifica → dashboard del viaggio |
| Esplorazione stagionale | `GetSeasonalHighlightsUseCase`, `SeasonalPoiFilter`, `SeasonalCalendar`, `PhotoSpotTagger` | Google Places API (New) + [Open-Meteo](https://open-meteo.com) | Temi del mese (es. mercatini di Natale a dicembre), stagioni invertite nell'emisfero sud, tag Instagrammabile/Panoramico/Tramonto/Chicca nascosta |
| Trasporti pubblici | `PlanTransitRouteUseCase`, `TransitRoute` (cambi, coincidenze, tempo a piedi) | Google Routes API (`TRANSIT`) | Percorsi multimodali metro/bus/tram/treni, coincidenze impossibili scartate, preferenze di routing |
| Ristorazione economica | `FindBudgetRestaurantsUseCase`, `BudgetDiningCriteria` | Google Places API (New) | Vincolo `price_level` 1–2 e valutazione ≥ 4,3, riapplicato sempre dal dominio |

### Quando un'offerta è "davvero conveniente"
Per ogni viaggio seguito PartiMo conserva lo storico dei prezzi migliori: il volo più economico e
l'alloggio con recensioni da 8/10 in su più economico (a notte). All'attivazione della campanella i
prezzi visti in quel momento diventano il primo riferimento. A ogni controllo:
- il **prezzo abituale** è la mediana degli ultimi 10 controlli (un picco isolato non la sposta);
- è un **affare** se il prezzo attuale è almeno il **15%** sotto il prezzo abituale;
- lo stesso affare non viene notificato due volte: serve un ulteriore calo del 5%, oppure che il
  prezzo torni normale e poi scenda di nuovo;
- gli avvisi di mesi ormai passati vengono rimossi; una ricerca fallita (es. offline) non altera lo storico.

### Cache delle ricerche
`ResponseCache` salva in Room le risposte (già validate) di ogni ricerca, identificate da una chiave
SHA-256 dei parametri. Il TTL dipende dal tipo di dato: voli 20 minuti, alloggi 1 ora, ristoranti
12 ore, POI 24 ore, meteo 15 minuti, trasporti 2 minuti, ricerca città 7 giorni. Se la rete non è
disponibile viene servito il dato scaduto, segnalato in UI con il badge "Offline · dati salvati".
**Aggiorna**, il pull-to-refresh e i controlli in background ignorano le cache ancora valide.

## Configurazione delle chiavi API

Le chiavi si leggono **solo** da `local.properties` (escluso dal VCS) oppure da variabili d'ambiente
(utile in CI), e vengono iniettate in `BuildConfig` da `app/build.gradle.kts`: non sono mai scritte
nel codice.

```properties
# local.properties
sdk.dir=C\:\\Android\\Sdk
DUFFEL_ACCESS_TOKEN=duffel_test_xxx      # voli + alloggi (token di test da app.duffel.com)
GOOGLE_MAPS_API_KEY=AIza...              # Places API (New) + Routes API abilitate
```

- **Modalità demo:** se una chiave è vuota, il modulo corrispondente usa dati dimostrativi generati
  per la città scelta (voli con durata e prezzo in base alla distanza reale, hotel e ristoranti di
  fantasia attorno al centro, luoghi generici con eventi stagionali) e la UI mostra il badge "Demo".
  I prezzi demo seguono un **mercato simulato**: cambiano ogni 10 minuti (±8%) e ogni tanto
  un'offerta va in promozione last minute (−30%), così "Aggiorna" e gli avvisi si possono provare
  anche senza chiavi. Ricerca città, aeroporti, consigli e meteo sono sempre reali, perché non
  richiedono chiavi.
- **Sicurezza:** una chiave in `BuildConfig` può essere estratta dall'APK. In Google Cloud Console
  limita la chiave Maps al package `com.partimo.app` e al certificato SHA-1. Il token Duffel è
  una credenziale lato server: in produzione le chiamate vanno instradate da un proprio backend
  (BFF). Il `DataSource` è già isolato per questo passaggio.

## Build e test

Requisiti: JDK 17 o superiore e Android SDK con la piattaforma 36. `JAVA_HOME` deve puntare a un JDK
esistente, altrimenti `gradlew` si ferma con "JAVA_HOME is set to an invalid directory". In
alternativa puoi aprire il progetto in Android Studio, che usa il suo JDK integrato.

```bash
./gradlew compileDebugKotlin        # compilazione Kotlin di tutti i moduli
./gradlew test                      # test unitari (domain, data, app)
./gradlew :app:assembleDebug        # APK di debug
```

223 test unitari:
- **`:domain` (107):** modelli e validazioni (compresi i dodici mesi di `TravelPeriod`), servizi di
  dominio (qualità/prezzo, stagionalità, scelta degli aeroporti, rilevamento degli affari) e tutti i
  casi d'uso, con fake condivisi tramite `testFixtures`.
- **`:data` (53):** cache e TTL, mappatura degli errori, client HTTP con risposte JSON simulate
  (Duffel, Places, Routes, Open-Meteo), dataset aeroporti reale (es. Roma → FCO, Parigi → CDG),
  catalogo delle mete, repository DataStore e formato di salvataggio, mercato simulato della demo.
- **`:app` (63):** ViewModel (dashboard, ricerca, scelta della partenza), rotte di navigazione,
  notifiche (Robolectric), formattazione e test UI Compose con Robolectric (`app/src/testDebug`), che
  salvano anche gli screenshot in `app/build/outputs/screenshots/`.

## Scelte tecniche

- **Amadeus Self-Service** è stato dismesso il 17/07/2026: per voli e hotel si usa Duffel, isolato
  dietro `FlightOffersDataSource`/`StayOffersDataSource`.
- **Places `minRating`** viene arrotondato dall'API *per eccesso* a multipli di 0,5 (4,3 → 4,5).
  Si invia quindi il multiplo inferiore (4,0) e la soglia esatta di 4,3 viene applicata dal dominio.
- **Meteo attuale:** influisce sui suggerimenti solo se la partenza è entro 2 giorni. Per viaggi
  lontani contano stagione e calendario. Con allerta meteo i luoghi all'aperto vengono esclusi.
- **Date del viaggio:** nei mesi futuri si parte il 10 del mese per quattro notti; nei prossimi
  giorni (o nel mese in corso, se il 10 è passato) si parte domani.
- **Versioni:** sono le più recenti compatibili con `compileSdk 36`, la piattaforma installata.
  Compose 1.12, core 1.19, lifecycle 2.11, Coil 3.6 e Ktor 3.6 (tramite OkHttp 5.5) richiedono
  `compileSdk 37`: per aggiornarle, installa la piattaforma 37 e alza `compileSdk`.
- **Aeroporti:** per ogni città viene proposto l'hub internazionale se non è più di 40 km più lontano
  dell'aeroporto più vicino (Roma → Fiumicino e non Ciampino), altrimenti un grande aeroporto,
  altrimenti il più vicino (Pisa → Pisa e non Bologna). Per la partenza la scelta finale è dell'utente.
- **Preferenze con DataStore:** la cache Room può essere svuotata senza conseguenze, mentre partenza
  e viaggi seguiti non vanno mai persi. Sono quindi salvati a parte, in JSON versionabile.
- **Notifiche:** su Android 13+ il permesso viene chiesto quando si attiva la prima campanella. Se
  viene negato l'avviso resta salvato e la snackbar porta alle impostazioni delle notifiche.
- **DI manuale** (`AppContainer`, `DataModule`): nessun annotation processor aggiuntivo. La
  struttura è pronta per Hilt o Koin.

## Struttura

```
domain/src/main/kotlin/com/partimo/domain/
  common/      DataResult, DataError
  model/       flight, stay, poi, weather, transit, dining, place (città, aeroporti, partenza, mete),
               deal (viaggi seguiti, affari), TravelPeriod, Trip, Money, GeoPoint
  repository/  interfacce dei repository
  service/     ValueForMoneyScorer, SeasonalPoiFilter, SeasonalCalendar, PhotoSpotTagger,
               AirportSelector, DealDetector
  usecase/     casi d'uso
domain/src/testFixtures/   fake dei repository e dati di test condivisi
data/src/main/kotlin/com/partimo/data/
  cache/       Room + ResponseCache (TTL, fallback offline)
  network/     HttpClientFactory, gestione errori
  remote/      duffel, places, routes, weather, geocoding
  local/       dataset aeroporti (asset), catalogo curato delle mete, preferenze (DataStore)
  demo/        catalogo e sorgenti demo (mercato simulato)
  repository/  implementazioni dei repository
  di/          DataModule
app/src/main/kotlin/com/partimo/app/
  di/             AppContainer
  navigation/     NavHost e rotte type-safe
  notifications/  DealCheckWorker, DealCheckScheduler, DealNotifier
  ui/common/      UiState, componenti condivisi, periodi, ricerca città, formattazione, testi
  ui/search/      schermata "Dove vuoi andare?" e "Consigliami"
  ui/departure/   scelta del punto di partenza
  ui/dashboard/   ViewModel, dashboard a sezioni, componenti, anteprime
app/src/test/        test di ViewModel, notifiche, stati UI e formattazione
app/src/testDebug/   test UI Compose con Robolectric (+ screenshot)
docs/                screenshot delle schermate
data/src/main/assets/airports.csv   aeroporti con voli di linea (OurAirports, pubblico dominio)
```
