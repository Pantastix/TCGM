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
import de.pantastix.project.shared.resources.MR
import dev.icerock.moko.resources.compose.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupabaseGuideScreen(onBack: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val sqlBefehle = """
        DROP VIEW IF EXISTS public."PokemonCardInfoView";
        DROP TABLE IF EXISTS public."PokemonCardEntity";
        DROP TABLE IF EXISTS public."SetEntity";

        CREATE TABLE public."SetEntity" (
            "setId" TEXT NOT NULL PRIMARY KEY,
            "tcgIoSetId" TEXT,
            "abbreviation" TEXT,
            "nameLocal" TEXT NOT NULL,
            "nameEn" TEXT NOT NULL,
            "logoUrl" TEXT,
            "cardCountOfficial" INTEGER NOT NULL,
            "cardCountTotal" INTEGER NOT NULL,
            "releaseDate" TEXT
        );

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
            FOREIGN KEY("setId") REFERENCES public."SetEntity"("setId") ON DELETE CASCADE,
            UNIQUE("tcgDexCardId", "language")
        );

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
                title = { Text(stringResource(MR.strings.guide_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MR.strings.guide_back_button_desc)
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
                titel = stringResource(MR.strings.guide_step1_title),
                beschreibung = stringResource(MR.strings.guide_step1_desc)
            )

            AnleitungSchritt(
                nummer = 2,
                titel = stringResource(MR.strings.guide_step2_title),
                beschreibung = stringResource(MR.strings.guide_step2_desc)
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
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(MR.strings.guide_copy_sql_button_desc), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(MR.strings.guide_copy_sql_button))
                        }
                    }
                }
            }

            AnleitungSchritt(
                nummer = 3,
                titel = stringResource(MR.strings.guide_step3_title),
                beschreibung = stringResource(MR.strings.guide_step3_desc)
            )

            AnleitungSchritt(
                nummer = 4,
                titel = stringResource(MR.strings.guide_step4_title),
                beschreibung = stringResource(MR.strings.guide_step4_desc)
            )
        }
    }
}

@Composable
private fun AnleitungSchritt(nummer: Int, titel: String, beschreibung: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "${stringResource(MR.strings.guide_step_prefix)} $nummer: $titel",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = beschreibung,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}