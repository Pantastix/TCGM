package de.pantastix.project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupabaseGuideScreen(onBack: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val sqlBefehle = """
        -- Löscht die alte View und Tabellen, falls sie existieren, um einen sauberen Start zu gewährleisten.
        -- Vorsicht: Dies löscht alle vorhandenen Daten!
        DROP VIEW IF EXISTS public."PokemonCardInfoView";
        DROP TABLE IF EXISTS public."PokemonCardEntity";
        DROP TABLE IF EXISTS public."SetEntity";

        -- Erstellt die Tabelle für die Set-Informationen.
        -- Die Spalte "setId" ist jetzt die ID von TCGdex.
        CREATE TABLE public."SetEntity" (
            "setId" TEXT NOT NULL PRIMARY KEY,
            "tcgIoSetId" TEXT, -- NEU: Speichert die ID von PokemonTCG.io, kann NULL sein.
            "abbreviation" TEXT,
            "nameLocal" TEXT NOT NULL,
            "nameEn" TEXT NOT NULL,
            "logoUrl" TEXT,
            "cardCountOfficial" INTEGER NOT NULL,
            "cardCountTotal" INTEGER NOT NULL,
            "releaseDate" TEXT
        );

        -- Erstellt die Tabelle für die Karten in der Sammlung.
        -- Der Foreign Key verweist weiterhin auf "setId".
        CREATE TABLE public."PokemonCardEntity" (
            "id" BIGSERIAL PRIMARY KEY,
            "setId" TEXT NOT NULL,
            "tcgDexCardId" TEXT NOT NULL,
            "nameLocal" TEXT NOT NULL,
            "nameEn" TEXT NOT NULL,
            "language" TEXT NOT NULL,
            "localId" TEXT NOT NULL,
            "imageUrl" TEXT,
            "cardMarketLink" TEXT,
            "ownedCopies" INTEGER NOT NULL DEFAULT 1,
            "notes" TEXT,
            "rarity" TEXT,
            "hp" INTEGER,
            "types" TEXT,
            "illustrator" TEXT,
            "stage" TEXT,
            "retreatCost" INTEGER,
            "regulationMark" TEXT,
            "currentPrice" DOUBLE PRECISION,
            "lastPriceUpdate" TEXT,
            "variantsJson" TEXT,
            "abilitiesJson" TEXT,
            "attacksJson" TEXT,
            "legalJson" TEXT,
            -- Stellt sicher, dass bei Löschung eines Sets auch alle zugehörigen Karten entfernt werden.
            FOREIGN KEY("setId") REFERENCES public."SetEntity"("setId") ON DELETE CASCADE,
            -- Stellt sicher, dass jede Karte pro Sprache nur einmal existieren kann.
            UNIQUE("tcgDexCardId", "language")
        );

        -- Erstellt eine View für eine vereinfachte und schnelle Abfrage der Kartenliste.
        -- Diese View bleibt unverändert, da die Verknüpfung über "setId" weiterhin korrekt ist.
        CREATE OR REPLACE VIEW public."PokemonCardInfoView" AS
        SELECT
            P."id",
            P."tcgDexCardId",
            P."language",
            P."nameLocal",
            S."nameLocal" AS "setName",
            P."imageUrl",
            P."ownedCopies",
            P."currentPrice"
        FROM
            public."PokemonCardEntity" AS P
        JOIN
            public."SetEntity" AS S ON P."setId" = S."setId";
    """.trimIndent()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Supabase Einrichtungsanleitung") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück zu den Einstellungen"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            AnleitungSchritt(
                nummer = 1,
                titel = "Supabase-Projekt erstellen",
                beschreibung = "Wenn sie noch kein Supabase Projekt haben, besuchen Sie supabase.com, erstellen Sie einen kostenlosen Account und legen Sie ein neues Projekt an. Wählen Sie eine Region in Ihrer Nähe (z.B. Frankfurt) und merken Sie sich Ihr Datenbank-Passwort."
            )

            AnleitungSchritt(
                nummer = 2,
                titel = "Datenbanktabellen anlegen",
                beschreibung = "ACHTUNG!!! Diesen schritt nur ausführen, wenn sie noch keine Datenbank besitzen!\nNavigieren Sie in Ihrem neuen Supabase-Projekt zum 'SQL Editor'. Klicken Sie auf '+ New query' und fügen Sie den gesamten folgenden Text in das Editor-Fenster ein. Klicken Sie anschließend auf 'RUN', um die Tabellen zu erstellen."
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Column {
                    Text(
                        text = sqlBefehle,
                        modifier = Modifier.padding(16.dp),
                        fontFamily = FontFamily.Monospace
                    )
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, end = 8.dp)) {
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(sqlBefehle))
                            },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "SQL kopieren", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("SQL Kopieren")
                        }
                    }
                }
            }

            AnleitungSchritt(
                nummer = 3,
                titel = "API-Schlüssel finden",
                beschreibung = "Navigieren Sie in den Projekt-Einstellungen zum Reiter 'API'. Dort finden Sie zwei wichtige Informationen, die Sie für die App benötigen:\n\n1. Die Projekt-URL (unter 'Project URL').\n2. Den 'public anon' API-Schlüssel (unter 'Project API Keys'). Kopieren Sie beide Werte."
            )

            AnleitungSchritt(
                nummer = 4,
                titel = "Schlüssel in die App eintragen",
                beschreibung = "Kehren Sie zur Einstellungsseite dieser App zurück. Fügen Sie die kopierte URL und den 'anon' Schlüssel in die entsprechenden Felder ein und klicken Sie auf 'Verbinden & Prüfen'."
            )
        }
    }
}

@Composable
private fun AnleitungSchritt(nummer: Int, titel: String, beschreibung: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Schritt $nummer: $titel",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = beschreibung,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}