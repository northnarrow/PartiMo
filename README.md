# PartiMo

App Android nativa (Kotlin) per organizzare viaggi in modo intelligente in qualunque città del mondo.
All'avvio chiede "Dove vuoi andare?": si cerca una città oppure si tocca **Consigliami** per ricevere
le mete più adatte al periodo scelto. Il **punto di partenza** lo decide l'utente (città e aeroporto)
e il periodo può essere "Prossimi giorni" o **uno qualunque dei prossimi dodici mesi**.

La meta scelta apre una dashboard divisa in sezioni, ognuna con il suo pulsante nella barra in basso:
**Voli**, **Alloggi**, **Da vedere**, **Trasporti**, **Ristoranti**. Il pulsante **Aggiorna** cerca le
offerte last minute ignorando la cache. La **campanella** attiva gli avvisi: PartiMo controlla i prezzi
in background e manda una notifica quando voli o alloggi diventano davvero convenienti.

In **Da vedere** i luoghi e le foto sono **reali anche senza nessuna chiave API** (arrivano da
Wikipedia). Toccando un luogo si apre la sua **scheda**: foto, breve descrizione, storia del luogo
divisa in capitoli e, sempre visibile in basso, il pulsante **Naviga** che apre Google Maps con il
percorso dalla posizione attuale fino al luogo.

Anche **alloggi** e **ristoranti** sono reali senza chiavi: le strutture ricettive e i locali attorno
al centro arrivano da **OpenStreetMap**. Ogni struttura ha **Vedi prezzi**, che apre Booking.com con
nome e date del viaggio già compilati; ogni ristorante si apre su Google Maps con recensioni, foto e
orari. Per **voli** e **trasporti** i prezzi e gli orari reali richiedono una chiave: senza chiave
l'app mostra delle stime e i pulsanti verso **Google Voli**, **Skyscanner** e **Google Maps** (percorso
con i mezzi), che si aprono con tratta e date del viaggio.

<img src="docs/search_ideas.png" alt="Schermata iniziale con partenza, mesi e consigli" width="240" /> <img src="docs/departure_picker.png" alt="Scelta dell'aeroporto di partenza" width="240" /> <img src="docs/trip_dashboard.png" alt="Dashboard: sezione Voli con i collegamenti a Google Voli e Skyscanner" width="240" /> <img src="docs/trip_dashboard_stays.png" alt="Dashboard: alloggi reali da OpenStreetMap con Booking.com e Airbnb" width="240" /> <img src="docs/trip_dashboard_explore.png" alt="Dashboard: sezione Da vedere" width="240" /> <img src="docs/place_detail.png" alt="Scheda di un luogo con descrizione, storia e pulsante Naviga" width="240" /> <img src="docs/trip_dashboard_transit.png" alt="Dashboard: trasporti con il percorso reale su Google Maps" width="240" /> <img src="docs/trip_dashboard_restaurants.png" alt="Dashboard: ristoranti reali da OpenStreetMap" width="240" />

Negli screenshot (generati dai test, senza rete) le foto sono segnaposto grigi: nell'app si caricano
le foto reali dei luoghi.

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
│ NavHost: Search → Dashboard (5 sezioni) → Scheda del luogo · Scelta partenza        │
│            ▲ StateFlow<…UiState> (schermate stateless)                              │
│ SearchVM · TripDashboardVM · PlaceDetailVM · DeparturePickerVM ── AppContainer      │
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
│ Wikipedia (luoghi) · OpenStreetMap (alloggi, ristoranti) · DataStore · DataModule    │
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
| Voli | `SearchFlightsUseCase`, `ValueForMoneyScorer` | [Duffel](https://duffel.com/docs) (offer requests) | Punteggio qualità/prezzo normalizzato. Sempre presenti i pulsanti **Google Voli** e **Skyscanner** con tratta e date compilate: senza chiave le tariffe mostrate sono stime e i pulsanti portano ai prezzi reali |
| Alloggi | `SearchAccommodationsUseCase`, `FindLodgingsUseCase` | Duffel Stays, oppure senza chiave [OpenStreetMap](https://www.openstreetmap.org) ([Overpass API](https://wiki.openstreetmap.org/wiki/Overpass_API)) | Con Duffel: offerte con prezzo, media bayesiana sulle recensioni, filtri e ordinamenti. Senza chiave: hotel, B&B, ostelli e appartamenti reali vicino al centro (tipo, stelle, indirizzo, distanza), ognuno con **Vedi prezzi** (Booking.com con nome e date), mappa e sito ufficiale; in alto Booking.com e Airbnb per tutta la città |
| Aggiorna (last minute) | `TripDashboardViewModel.refresh`, `PriceChange` | – | Ricarica tutto ignorando la cache e riassume la variazione dei prezzi migliori (es. "🔥 Volo sceso a 89 € (−12 €)") |
| Avvisi sulle offerte | `SetPriceAlertUseCase`, `CheckPriceWatchesUseCase`, `DealDetector` | WorkManager + notifiche | Controllo ogni 6 ore con rete disponibile; notifica se volo o alloggio (ben recensito) costa almeno il 15% in meno del solito; tocco sulla notifica → dashboard del viaggio |
| Da vedere (esplorazione stagionale) | `GetSeasonalHighlightsUseCase`, `SeasonalPoiFilter`, `SeasonalCalendar`, `PhotoSpotTagger` | [Wikipedia](https://www.mediawiki.org/wiki/API:Main_page) (gratuita, senza chiave) oppure Google Places API (New), + [Open-Meteo](https://open-meteo.com) | Senza chiave: luoghi e foto reali da Wikipedia, dal più noto al meno noto, senza città, enti, stazioni, eventi storici ed edifici scomparsi. Con la chiave Google anche valutazioni e temi del mese (es. mercatini di Natale a dicembre). Stagioni invertite nell'emisfero sud, tag Instagrammabile/Panoramico/Tramonto/Chicca nascosta |
| Scheda del luogo e "Naviga" | `GetPoiDetailsUseCase`, `Excerpt` | Wikipedia (testo, foto, crediti da Wikimedia Commons) + [Google Maps URLs](https://developers.google.com/maps/documentation/urls/get-started) | Tocco su un luogo: foto in alta risoluzione, breve descrizione, storia in capitoli (es. Costruzione → Medioevo → restauri), fonti e licenze. **Naviga** apre l'app Google Maps (o il browser) con il percorso fino al luogo, senza chiave. Per i luoghi di Google la voce si cerca per nome vicino alle coordinate |
| Trasporti pubblici | `PlanTransitRouteUseCase`, `TransitRoute` (cambi, coincidenze, tempo a piedi) | Google Routes API (`TRANSIT`) + Google Maps URLs | Percorsi multimodali metro/bus/tram/treni, coincidenze impossibili scartate, preferenze di routing. **Apri in Google Maps** mostra lo stesso percorso con linee e orari reali anche senza chiave |
| Ristoranti | `FindBudgetRestaurantsUseCase`, `BudgetDiningCriteria` | Google Places API (New), oppure senza chiave OpenStreetMap (Overpass API) | Con Google: vincolo `price_level` 1–2 e valutazione ≥ 4,3, riapplicato sempre dal dominio. Senza chiave: locali reali vicino al centro (cucina, indirizzo, distanza), prima i più completi e vicini, catene in fondo. Il tocco apre il locale su Google Maps (recensioni, foto, orari) |

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
12 ore, POI e strutture ricettive 24 ore, meteo 15 minuti, trasporti 2 minuti, ricerca città e voci di Wikipedia 7 giorni. Se la rete non è
disponibile viene servito il dato scaduto, segnalato in UI con il badge "Offline · dati salvati".
**Aggiorna**, il pull-to-refresh e i controlli in background ignorano le cache ancora valide.

## Configurazione delle chiavi API

Le chiavi API sono **personali**: sono legate al tuo account (e, per Google, alla fatturazione), quindi
ognuno deve crearle per sé e **non vanno mai inserite nel codice né committate**. Chi le trovasse nel
repository potrebbe usarle a tue spese. PartiMo le legge **solo** da `local.properties` (escluso dal
VCS) oppure da variabili d'ambiente (utile in CI), e le inietta in `BuildConfig` da
`app/build.gradle.kts`.

### Cosa funziona con e senza chiavi

| Funzione | Senza chiavi | Con le chiavi |
|---|---|---|
| Da vedere: luoghi, foto, descrizione, storia | ✅ Reale (Wikipedia) | ✅ Reale (Google Places + storia da Wikipedia) |
| Naviga (Google Maps) | ✅ Reale, non serve chiave | ✅ Reale |
| Ricerca città, aeroporti, Consigliami, meteo | ✅ Reale | ✅ Reale |
| Alloggi | ✅ Strutture reali (OpenStreetMap) + Booking.com e Airbnb con le date del viaggio | Offerte con prezzo di Duffel Stays (`DUFFEL_ACCESS_TOKEN`) |
| Ristoranti | ✅ Locali reali (OpenStreetMap), tocco → Google Maps | Google Places API (`GOOGLE_MAPS_API_KEY`): valutazioni, fasce di prezzo, foto |
| Voli | Stime + ✅ Google Voli e Skyscanner con tratta e date (prezzi reali sul sito) | Offerte Duffel nell'app (`DUFFEL_ACCESS_TOKEN`) |
| Trasporti pubblici | Stime + ✅ percorso reale su Google Maps | Percorsi reali nell'app, Google Routes API (`GOOGLE_MAPS_API_KEY`) |

I dati di OpenStreetMap, Wikipedia e Open-Meteo e i collegamenti ai siti non richiedono chiavi né
registrazione: per questo quelle funzioni sono reali in qualunque configurazione.

### 1. Token Duffel (voli e alloggi)
1. Registrati gratuitamente su [app.duffel.com](https://app.duffel.com).
2. Nella dashboard, in modalità *Developer test mode*, crea un token in **Access tokens**. Un token
   di test inizia con `duffel_test_` e non spende denaro: le offerte arrivano dalla compagnia di
   prova "Duffel Airways" ([test mode](https://duffel.com/docs/api/overview/test-mode)).
3. Per le offerte reali delle compagnie serve un token `duffel_live_`, disponibile dopo aver
   attivato la modalità live dell'account.
4. Gli alloggi usano **Duffel Stays**, che va
   [richiesto a Duffel](https://duffel.com/docs/guides/getting-started-with-stays): finché non è
   abilitato, la sezione Alloggi mostra "Chiave API mancante o non valida" (i voli funzionano comunque).
   Senza token la sezione Alloggi mostra le strutture reali di OpenStreetMap.

### 2. Chiave Google Maps Platform (ristoranti, trasporti e, facoltativo, Da vedere)
1. Apri la [Google Cloud Console](https://console.cloud.google.com), crea un progetto e collega un
   account di fatturazione (Google offre una quota mensile gratuita: controlla il listino).
2. In *API e servizi → Libreria* abilita **Places API (New)** e **Routes API**.
3. In *API e servizi → Credenziali* scegli *Crea credenziali → Chiave API*.
4. Limita la chiave: in *Restrizioni delle applicazioni* scegli **App Android** e aggiungi il package
   `com.partimo.app` con l'impronta SHA-1 del certificato di firma (`./gradlew signingReport`: debug e
   release hanno impronte diverse); in *Restrizioni API* consenti solo Places API (New) e Routes API.
   PartiMo invia package e impronta del certificato con le intestazioni `X-Android-Package` e
   `X-Android-Cert`, necessarie perché Google accetti una chiave con restrizione Android nelle
   chiamate REST (anche per le foto di Places).

### 3. Inserisci le chiavi in `local.properties`

Il file si trova nella cartella principale del progetto (Android Studio lo crea con `sdk.dir`):

```properties
# local.properties
sdk.dir=C\:\\Android\\Sdk
# Voli e alloggi (Duffel)
DUFFEL_ACCESS_TOKEN=duffel_test_xxx
# Ristoranti e trasporti (Google Maps Platform: Places API (New) + Routes API)
GOOGLE_MAPS_API_KEY=AIza...
```

Nei file `.properties` i commenti vanno su una riga a sé: un `#` scritto dopo il valore farebbe parte
del valore (per sicurezza la build ignora comunque tutto ciò che segue il primo spazio, perché le
chiavi non contengono spazi). Poi ricompila l'app: le chiavi vengono lette in fase di build. In CI si
usano variabili d'ambiente con gli stessi nomi (es. i *secrets* di GitHub Actions). Se una chiave finisce per errore in un commit o
in una chat, revocala e creane una nuova.

- **Senza chiavi:** voli e trasporti usano stime generate per la città scelta (voli con durata e
  prezzo in base alla distanza reale, percorsi generici dall'aeroporto) con il badge "Demo", e i
  pulsanti verso Google Voli, Skyscanner e Google Maps portano ai prezzi e agli orari reali. I prezzi
  stimati seguono un **mercato simulato**: cambiano ogni 10 minuti (±8%) e ogni tanto un'offerta va
  in promozione last minute (−30%), così "Aggiorna" e gli avvisi si possono provare anche senza
  chiavi. Alloggi, ristoranti, ricerca città, aeroporti, consigli, meteo e luoghi da vedere sono
  sempre reali.
- **Sicurezza:** una chiave in `BuildConfig` può essere estratta dall'APK e le intestazioni Android
  si possono imitare: la restrizione riduce gli abusi ma non li impedisce. Il token Duffel è una
  credenziale lato server: in produzione le chiamate a Duffel e Google vanno instradate da un proprio
  backend (BFF), che custodisce le chiavi. I `DataSource` sono già isolati per questo passaggio.

## Build e test

Requisiti: JDK 17 o superiore e Android SDK con la piattaforma 36. `JAVA_HOME` deve puntare a un JDK
esistente, altrimenti `gradlew` si ferma con "JAVA_HOME is set to an invalid directory". In
alternativa puoi aprire il progetto in Android Studio, che usa il suo JDK integrato.

```bash
./gradlew compileDebugKotlin        # compilazione Kotlin di tutti i moduli
./gradlew test                      # test unitari (domain, data, app)
./gradlew :app:assembleDebug        # APK di debug
```

292 test unitari:
- **`:domain` (130):** modelli e validazioni (compresi i dodici mesi di `TravelPeriod`), servizi di
  dominio (qualità/prezzo, stagionalità, notorietà dei luoghi, scelta degli aeroporti, rilevamento
  degli affari, estratti brevi di descrizione e storia) e tutti i casi d'uso, compresi ristoranti
  senza valutazioni e strutture ricettive, con fake condivisi tramite `testFixtures`.
- **`:data` (77):** cache e TTL, mappatura degli errori, client HTTP con risposte JSON simulate
  (Duffel, Places, Routes, Open-Meteo, Wikipedia, Overpass), classificazione dei luoghi su risposte
  **reali** di Wikipedia (Roma, Lisbona, Colosseo in `data/src/test/resources/wikipedia`), ristoranti
  di Vienna e strutture di Matera da risposte **reali** di OpenStreetMap (`data/src/test/resources/osm`),
  cambio di istanza Overpass se sovraccarica, intestazioni per le chiavi Google con restrizione
  Android, dataset aeroporti reale (es. Roma → FCO, Parigi → CDG), catalogo delle mete, repository
  DataStore e formato di salvataggio, mercato simulato della demo.
- **`:app` (85):** ViewModel (dashboard con e senza chiavi, ricerca, scelta della partenza, scheda del
  luogo), rotte di navigazione, collegamenti a Google Maps, Google Voli, Skyscanner, Booking.com e
  Airbnb, User-Agent delle foto, notifiche (Robolectric), formattazione e test UI Compose con
  Robolectric (`app/src/testDebug`), che salvano anche gli screenshot in `app/build/outputs/screenshots/`.

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
- **Luoghi da Wikipedia:** ricerca `nearcoord` con il profilo di ranking `popular_inclinks_pv`
  (visite e link in entrata), così i luoghi più noti vengono per primi. Wikipedia filtra per
  distanza, mentre Google usa il raggio solo come preferenza: si cerca quindi in un raggio doppio
  (10 km, es. Schönbrunn a Vienna). Il classificatore legge la descrizione breve della voce (la
  prima parola chiave vince, a parità di posizione la più lunga) e scarta città, enti, università,
  stazioni, stadi, eventi, opere custodite nei musei ed edifici scomparsi. Con meno di 8 luoghi
  nella lingua dell'app si aggiungono quelli della Wikipedia inglese, senza duplicati (stesso
  elemento Wikidata).
- **User-Agent:** Wikimedia risponde **403** alle foto richieste con lo User-Agent predefinito di
  OkHttp e limita a 10 richieste al minuto le API senza un User-Agent identificabile. PartiMo invia
  `PartiMo/<versione> (https://github.com/northnarrow/partimo; Android)` sia dalle API (Ktor) sia
  dalle foto (Coil, con al massimo 3 richieste contemporanee per host).
- **Storia breve:** la descrizione è l'introduzione della voce (al massimo 2 paragrafi e 650
  caratteri); la storia è la sezione "Storia" (o "History", "Origini"…) resa come linea del tempo:
  il primo paragrafo di ogni sottosezione, fino a 5 capitoli, tagliato alla fine di una frase.
- **Naviga:** usa le [Maps URLs](https://developers.google.com/maps/documentation/urls/get-started)
  (`https://www.google.com/maps/dir/?api=1&destination=lat,lng`): nessuna chiave, apre l'app Google
  Maps se installata, altrimenti il browser, e lascia a Maps la scelta del mezzo (a piedi, mezzi, auto).
- **Licenze:** i testi di Wikipedia sono CC BY-SA 4.0 e le foto di Wikimedia Commons hanno licenze
  libere con attribuzione: la scheda mostra fonte, autore e licenza, con i collegamenti alle pagine
  originali. I dati di OpenStreetMap sono ODbL: sotto alloggi e ristoranti c'è "Dati © OpenStreetMap
  contributors", che apre la [pagina del copyright](https://www.openstreetmap.org/copyright).
- **OpenStreetMap (Overpass API):** una sola query per sezione, con gli elementi che hanno un nome
  attorno al centro (ristoranti entro 1,5 km, strutture entro 2 km, centro delle aree con `out
  center`). Le istanze pubbliche sono gratuite ma chiedono un uso leggero e uno User-Agent
  identificabile: le risposte restano in cache (12 ore i ristoranti, 24 ore le strutture) e, se
  un'istanza risponde 429, 5xx o va in timeout, si prova la successiva; una richiesta rifiutata (4xx)
  non si ripete altrove. Per un'app pubblicata con molti utenti conviene un'istanza Overpass propria o
  un backend con cache. OpenStreetMap non ha valutazioni: i locali sono ordinati per completezza
  della scheda (cucina, orari, sito, voce Wikidata) e vicinanza, con le catene in fondo.
- **Collegamenti ai siti di viaggio** (`TravelLinks`, `MapsLinks`): URL pubblici con la ricerca già
  compilata (es. `booking.com/searchresults.it.html?ss=Vienna&checkin=…&checkout=…`), senza chiavi,
  accordi o codici di affiliazione. Dall'app escono solo tratta, città, date e numero di viaggiatori.
  I collegamenti a Google Maps si aprono nell'app Maps se installata.
- **Trasporti senza chiave:** [Transitous](https://transitous.org) offre percorsi reali senza chiave,
  ma solo per app open source non commerciali e previo contatto con il progetto: per ora non è
  attivo e senza chiave Google il percorso reale si apre in Google Maps.

## Struttura

```
domain/src/main/kotlin/com/partimo/domain/
  common/      DataResult, DataError
  model/       flight, stay, poi, weather, transit, dining, place (città, aeroporti, partenza, mete),
               deal (viaggi seguiti, affari), TravelPeriod, Trip, Money, GeoPoint
  repository/  interfacce dei repository
  service/     ValueForMoneyScorer, SeasonalPoiFilter, SeasonalCalendar, PhotoSpotTagger,
               AirportSelector, DealDetector, Excerpt (estratti brevi di descrizione e storia)
  usecase/     casi d'uso
domain/src/testFixtures/   fake dei repository e dati di test condivisi
data/src/main/kotlin/com/partimo/data/
  cache/       Room + ResponseCache (TTL, fallback offline)
  network/     HttpClientFactory, gestione errori, intestazioni per le chiavi Google Android
  remote/      duffel, places, routes, weather, geocoding, wikipedia, osm (Overpass API)
  local/       dataset aeroporti (asset), catalogo curato delle mete, preferenze (DataStore)
  demo/        catalogo e sorgenti demo (mercato simulato)
  repository/  implementazioni dei repository
  di/          DataModule
app/src/main/kotlin/com/partimo/app/
  di/             AppContainer, User-Agent e identità dell'app per le API
  navigation/     NavHost e rotte type-safe
  notifications/  DealCheckWorker, DealCheckScheduler, DealNotifier
  ui/common/      UiState, componenti condivisi, periodi, ricerca città, formattazione, testi,
                  collegamenti ai siti di viaggio (TravelLinks)
  ui/search/      schermata "Dove vuoi andare?" e "Consigliami"
  ui/departure/   scelta del punto di partenza
  ui/dashboard/   ViewModel, dashboard a sezioni, componenti, anteprime
  ui/place/       scheda del luogo (descrizione, storia, fonti) e collegamenti a Google Maps
app/src/test/        test di ViewModel, notifiche, stati UI e formattazione
app/src/testDebug/   test UI Compose con Robolectric (+ screenshot)
docs/                screenshot delle schermate
data/src/test/resources/wikipedia/  risposte reali di Wikipedia usate nei test
data/src/test/resources/osm/        risposte reali di OpenStreetMap (Overpass) usate nei test
data/src/main/assets/airports.csv   aeroporti con voli di linea (OurAirports, pubblico dominio)
```
