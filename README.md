# AVATAR: O Despertar dos Quatro Elementos (Minecraft 1.21.1 · Fabric)

Só precisa do **Fabric API**. Funciona em servidor dedicado e multiplayer.

## Primeiros passos
1. Ao entrar no mundo pela primeira vez abre a tela **Escolha seu elemento**: Fogo, Água, Terra, Ar e os novos **Raio, Gelo, Sombra e Espírito**.
2. Teclas (mudáveis em Controles > Avatar):
   - **Z X C V B N M** = as 7 técnicas do seu elemento
   - **G** = técnica especial selecionada (segure para carregar), **R** = próxima especial
   - **H** = Estado Avatar, **Y** = trocar de elemento (no Estado Avatar), **U** = menu de progresso
3. Os rangs: Aprendiz, Dobrador, Especialista, Mestre, Grande Mestre, Lenda. Usar poderes dá domínio (XP).

## Elementos novos (7 técnicas cada)
- **Raio**: Faísca, Arco Elétrico, Campo Estático, Passo Relâmpago, Trovão, Corrente de Raios, Julgamento do Trovão. Secreta: **Sobrecarga**.
- **Gelo**: Estilhaço, Sopro Gélido, Armadura de Gelo, Muralha de Gelo, Chuva de Granizo, Congelamento Total, Cometa Glacial. Secreta: **Zero Absoluto**.
- **Sombra**: Dardo Sombrio, Tentáculos, Passo Sombrio, Manto de Sombras, Orbe do Vazio, Pesadelo, Eclipse. Secreta: **Clones Sombrios**.
- **Espírito**: Pulso Espiritual, Luz Curativa, Espírito Guardião, Forma Etérea, Drenar Alma, Onda Espectral, Ascensão. Secreta: **Despertar Espiritual**.
As secretas novas liberam com 25 mortes usando o elemento (e o rank pedido), ou com o Pergaminho Antigo.

## Câmera em primeira pessoa
Ao usar uma técnica o campo de visão dá um pulso, a tela pisca na cor do elemento e a câmera treme de leve. Ao segurar uma técnica carregável a visão aproxima, as bordas fecham e cubos se juntam na sua mão.

## Técnicas secretas (desafios)
Raios (20 raios) · Combustão (20 mortes com Fogo) · Dobra de Sangue (20 mortes com Água, só à noite) · Cura Avançada (200 de vida curada) · Estilhaços de Metal (15 usos de Dobra de Metal) · Lava (30 mortes com Terra) · Sismologia (10 terremotos) · Projeção Espiritual (2400 ticks de voo) · Controle de Pressão (25 mortes com Ar). Também dá para usar o **Pergaminho Antigo** (+300 de domínio e a próxima técnica secreta).

## Estado Avatar
Chegue ao rank **Lenda** e use a **Gema Espiritual**. Aperte H: os quatro elementos orbitam você, técnicas mais fortes e baratas, e libera as combinações (Tornado Flamejante, Tempestade, Erupção Vulcânica, Prisão de Lama, Explosão de Vapor) e o **Colapso Elemental**.

## Itens
- Pergaminho Antigo: lápis, papel, diamante (`LPL/PDP/LPL`)
- Gema Espiritual: olho do fim, bloco de diamante, estrela do Nether (`EDE/DND/EDE`)

## Comandos (op)
`/avatar element <fire|water|earth|air|thunder|ice|shadow|spirit>` (também em português: fogo, agua, terra, ar, raio, gelo, sombra, espirito) · `/avatar choose` · `/avatar rank <0-5>` · `/avatar master` · `/avatar unlock` · `/avatar avatar` · `/avatar energy <n>` · `/avatar reset`

## Configuração
`config/avatar.json`: multiplicadores de custo/recarga/dano/energia, `allowPvp`, `modifyBlocks` (false = nenhum bloco é alterado), `restoreTicks` (blocos voltam ao normal), `craters`, `fireIgnites`, `spawnProtectionRadius`, `maxBlocksPerCast`, `particleDensity`, `cameraShake`, `secretsPermissionLevel`, `avatarStatePermissionLevel`.
