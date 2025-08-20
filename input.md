> Classificação de confidencialidade: Restrito
>
> <img src="./ygjtatey.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460
>
> **MONTREAL** **SISTEMA** **DE** **INTIMAÇÕES** **E** **APREENSÃO**
> **DE** **VEÍCULOS** **-** **Recupera**
>
> **ELABORADO** **POR:** Fábrica de Software - Montreal
>
> **Integração** **via** **Webservice**
>
> **para** **envio** **de** **notificações,** **composto** **por:**
> **Credor,** **Devedor** **(endereços,** **contatos,** **veículos),**
> **Garantidor,** **Serventia** **e** **Contrato.**

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./s3eowssr.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460

**Sumário**

**1** **-** **Histórico** **do**
**documento..........................................................................................**
**3** **2** **-**
**Descrição................................................................................................................**
**4**

**3** **-** **Etapas** **do** **Processamento** **–** **versão**
**Síncrona..........................................................**
**4**

> 3.1. Busca de dados por período na Montreal/RJ
> ........................................................................................................4
>
> 3.2. Retorno de dados por período à Montreal/MG
> ....................................................................................................4
>
> 3.3. Envio de dados à
> Montreal/RJ...............................................................................................................................4
>
> 3.4. Retorno de dados à Montreal/MG
> ........................................................................................................................5
>
> 3.5.
> Frequências............................................................................................................................................................5
>
> 3.6. Resultado
> ...............................................................................................................................................................5
>
> 3.7. Erros no envio dos
> dados.......................................................................................................................................5

**4** **-** **Processo** **de**
**Homologação......................................................................................**
**6**

**5** **-** **Especificação** **dos** **Serviços**
**.....................................................................................**
**7**
Autenticação:........................................................................................................................................................7

> Solicitação de Consulta por período Notificações – Consulta por
> período ..........................................................9
>
> Solicitação de Consulta por período de notificações -
> Modelo............................................................................9
>
> Resultado de Consulta por período Notificações – Consulta por período
> .........................................................10
>
> Resultado de Consulta por período de notificações -
> Modelo...........................................................................10
>
> Solicitação de Busca de Notificações/contratos - CONSULTA
> ............................................................................12
>
> Solicitação de Consulta – Busca de Notificações/contratos - Modelo
> ...............................................................14
>
> Solicitação de Consulta – Busca de Notificações/contratos -
> RETORNO............................................................19
>
> Solicitação de Consulta por período de notificações canceladas –
> Consulta por período.................................23
>
> Solicitação de Consulta por período de notificações canceladas -
> Modelo .......................................................23
>
> Resultado de Consulta por período de notificações canceladas –
> Consulta por período ..................................24
>
> Resultado de Consulta por período de notificações canceladas -
> Modelo.........................................................24
>
> Tabela de Mensagens de Erro Montreal/RJ de
> consulta:...................................................................................25
>
> URL para acesso ao ambiente de
> homologação.................................................................................................26
>
> Tipos de Dados dos JSON enviados e
> recebidos.................................................................................................27
>
> Críticas nos dados
> enviados................................................................................................................................27
>
> Outras regras operacionais a serem
> consideradas.............................................................................................27

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./vwnjz1j3.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460
>
> **1** **-** **Histórico** **do** **documento**

||
||
||
||
||
||

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./dn1iywtz.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460
>
> **2** **-** **Descrição**

Este webservice tem a finalidade de permitir a troca de dados entre a
Montreal/RJ e a Montreal/MG, para envio de notificações, credores,
devedores, serventias, veículos e contatos onde ocorrerá,
posteriormente, o cadastro de Escritórios de cobrança, Guincho, Pátios,
Localizadores e Leiloeiros, sem a necessidade de interlocução com o
sistema online de intimações e apreensão de veículos.

> **3** **-** **Etapas** **do** **Processamento** **–** **versão**
> **Síncrona**

O processamento neste caso é síncrono, ou seja, a cada busca recebida,
os dados são retornados à Montreal/MG, conforme imagem e descrição
abaixo:

> 1-Busca Dados
>
> 2-Retorna dados
>
> Montreal/MG Montreal/RJ
>
> 3-Dados de consulta
>
> 4-Resultados
>
> **3.1.** **Busca** **de** **dados** **por** **período** **na**
> **Montreal/RJ**

Nesta etapa, a Montreal/MG, através do webservice descrito neste manual,
envia sua busca de dados por período à Montreal/RJ. O retorno do
webservice pode conter tanto o resultado de críticas nos dados enviados
(feitas pela Montreal/RJ), quanto o resultado do processamento destes
dados, ou seja, o sucesso no resultado significa que a Montreal/RJ
acatou as informações enviadas pela Montreal/MG.

> **3.2.** **Retorno** **de** **dados** **por** **período** **à**
> **Montreal/MG**

Nesta etapa a Montreal/RJ retorna os dados oriundos dos campos de busca
solicitados pela Montreal/MG, recebe os resultados e disponibiliza em
sua base de dados e/ou sistema. Se a operação de registro de contrato
for bem-sucedida, o cliente final poderá seguir com o fluxo do processo
e emitir documentos.

> **3.3.** **Envio** **de** **dados** **à** **Montreal/RJ**

Nesta etapa, a Montreal/MG, através do webservice descrito neste manual,
envia seus dados de consulta para a Montreal/RJ. O retorno do webservice
pode conter tanto o resultado de críticas nos dados enviados (feitas
pela Montreal/RJ), quanto o resultado do processamento destes dados, ou
seja, o sucesso no resultado significa que a Montreal/RJ acatou as
informações enviadas pela Montreal/MG.

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./tgrb1dhm.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460
>
> **3.4.** **Retorno** **de** **dados** **à** **Montreal/MG**

Nesta etapa a Montreal/RJ retorna os dados oriundos dos campos de busca
solicitados pela Montreal/MG, recebe os resultados e disponibiliza em
sua base de dados e/ou sistema. Se a operação de registro de contrato
for bem-sucedida, o cliente final poderá seguir com o fluxo do processo
e emitir documentos.

> **3.5.** **Frequências**

A Montreal/RJ retornará os dados após a identificação da busca realizada
com sucesso. Isso significa que as informações das intimações ocorrem
tão logo os dados estarem armazenados na base de dados da Montreal/RJ e
ao longo do dia em uma operação 24 x 7.

OBS: não deixe de verificar as informações do tópico “*<u>Outras regras
operacionais a serem consideradas</u>*”.

> **3.6.** **Resultado**

Mesmo tendo recebido o resultado imediatamente após o envio de dados. A
Montreal/MG fica livre para repetir a consulta e obter o resultado
novamente através desta nova busca.

Nesta etapa a Montreal/MG consulta a situação do processamento na base
da Montreal/RJ. Os resultados possíveis são:

> • *200* – indica que obteve sucesso e disponibiliza os dados para
> visualização.
>
> • *401* – indica que obteve resposta de não autorização para
> prosseguir.
>
> • *403* – indica que a busca foi perdida.
>
> • *404* – indica que não foi encontrada.
>
> **3.7.** **Erros** **no** **envio** **dos** **dados**

A Montreal mantém seus sistemas ativos e em processamento ao longo do
dia em uma operação 24 x 7. Desta forma, a qualquer momento os dados
podem ser enviados e processados.

OBS: não deixe de verificar as informações do tópico “*<u>Outras regras
operacionais a serem consideradas</u>*”.

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./srdnuqav.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460
>
> **4** **-** **Processo** **de** **Homologação**

Para realização do processo de homologação, a empresa solicitante deve
realizar as seguintes etapas necessárias:

> a\) Solicitação à Montreal/RJ de login e senha para o ambiente de
> homologação para envio das consultas.
>
> b\) URL para realização da integração

Obs.: cada login dará acesso ao envio de notificações/contratos.

Após a homologação bem-sucedida, a Montreal/MG receberá login e senha
para o ambiente de produção para início das buscas e obtenção de
resultados.

||
||
||
||

> Classificação de confidencialidade:
> Restrito<img src="./0wfh3di0.png"
> style="width:6.55764in;height:2.31389in" />
>
> <img src="./4u3r1o1g.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460
>
> **5** **-** **Especificação** **dos** **Serviços**

**<u>Parâmetros mencionados nos serviços</u>**:

> • usuario e senha (string alfanumérica, tamanho max 12, min 8) • token
> jwt (string alfanumérica ilimitada)
>
> • Path ( string alfanumérica ilimitada)

**<u>Autenticação:</u>** via Token JWT

Descrição: Método de autenticação que recebe usuário e senha. Caso sejam
válidos, é retornando o objeto contendo o token jwt. Este token é
enviado como chave de segurança para as demais operações da API.

> **Rota:** **/api/sanctum/token**

Parâmetros: usuario e senha

Retorno: AuthDTO (objeto contendo informações do usuário e do token jwt
de sessão gerado)

<img src="./pfpf4iev.png"
style="width:3.725in;height:2.65833in" />1-Acessar o Swagger e fazer a
autenticação

||
||
||
||

> Classificação de confidencialidade:
> Restrito<img src="./5lp0z0jm.png"
> style="width:4.69167in;height:2.50833in" /><img src="./pmm1jzft.png"
> style="width:1.96875in;height:0.53125in" /><img src="./zct43jkh.png"
> style="width:4.03333in;height:1.96667in" />
>
> <img src="./pgimke0u.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460

2-Obtendo o sucesso, deve-se capturar o token gerado.

3-Selecionar ‘Authorize’ (observe que o cadeado está aberto).

4-Na tela a seguir, inserir o token em ‘Value’ e selecionar ‘Authorize’.

<img src="./uxjk15cr.png"
style="width:1.96875in;height:0.60417in" />Depois de clicar em Close e o
cadeado estará fechado, confirmando a autorização.

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./tldp4lmc.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460

**<u>Solicitação de Consulta por período Notificações – Consulta por
período</u>**

O layout do JSON com as informações relativas à obrigatoriedade de
preenchimento e tamanho limite das informações se encontra abaixo.

OBS: a relação abaixo indica o tipo de dado final. Todas as informações
podem ser enviadas como String, conforme o exemplo. Pelo menos uma é
obrigatória.

||
||
||
||
||

> **Rota:** **/api/recepcaoContrato/periodo/{data_inicio}/{data_fim}**
>
> **Descrição**: Método inicial a ser chamado para iniciar a consulta
> por período de notificações
>
> Parâmetros: token jwt, JSON descrito acima
>
> Retorno: JSON descrito a seguir.

**<u>Solicitação de Consulta por período de notificações - Modelo</u>**

***<u>Para consulta:</u>***

{

> "data_inicio": "2024-08-15", "data_fim": "2024-08-16"

<img src="./0kmd33z2.png"
style="width:3.55347in;height:1.74153in" />}

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./tcy4xpkq.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460

**<u>Resultado de Consulta por período Notificações – Consulta por
período</u>**

O layout do JSON com as informações relativas à obrigatoriedade de
preenchimento e tamanho limite das informações se encontra abaixo.

OBS: a relação abaixo indica o tipo de dado final. Todas as informações
podem ser enviadas como String, conforme o exemplo. Pelo menos uma é
obrigatória.

||
||
||
||
||
||
||
||
||
||
||

**<u>Resultado de Consulta por período de notificações - Modelo</u>**

{

> "nome_credor": "Banco Bradesco Financiamentos", "data_pedido":
> "2024-07-26 13:51:38.029684", "numero_contrato": "ABC123456",
>
> "etapa": "Aguardar prazo legal", "data_movimentacao": "2024-07-26
> 13:58:23", "veiculos": \[
>
> {
>
> "uf_emplacamento": "BA", "placa": "JLB2759",
>
> "modelo": "Palio HLX 1.8 mpi Flex 8V 4p" },
>
> {
>
> "uf_emplacamento": "BA", "placa": "JLB7777",
>
> "modelo": "HB20 Comfort Plus 1.0 8V 4p" }

\] }

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./1eognibh.png"
> style="width:7.35611in;height:0.55208in" /><img src="./c50t0ph3.png"
> style="width:4.50528in;height:3.14722in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./jhrsevo1.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460

**<u>Solicitação de Busca de Notificações/contratos - CONSULTA</u>**

O layout do JSON com as informações relativas à obrigatoriedade de
preenchimento e tamanho limite das informações se encontra abaixo.

OBS: a relação abaixo indica o tipo de dado final. Todas as informações
podem ser enviadas como String, conforme o exemplo. Pelo menos uma é
obrigatória.

Observação:

> a\) Se escolher uma das opções: ‘Nome credor’ ou ‘Uf de emplacamento’
> ou ‘Modelo do veículo’ ou ‘Etapa’, será obrigatório o preenchimento de
> outro campo diferente destes.
>
> 1\. Exemplo:
>
> i\. Se escolher ‘Nome credor’, deverá preencher ‘Data do pedido’ ou
> ‘Número do contrato’ ou ‘Data da movimentação’ ou ‘Placa’.
>
> **Rota:** **/api/recepcaoContrato/receber**
>
> **Descrição**: Método inicial a ser chamado por quem deseja iniciar a
> busca de notificações/contratos.
>
> Parâmetros: token jwt, JSON descrito acima
>
> Retorno: JSON descrito a seguir. (Ainda com 4 parâmetros, será
> atualizado assim que ajustar para os campos identificados em reunião
> com Yara e Lúcia)

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./knwgn3rq.png"
> style="width:7.35611in;height:0.55208in" /><img src="./uh0lc2pc.png"
> style="width:7.26805in;height:4.00694in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./0vfxn1me.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460

**<u>Solicitação de Consulta – Busca de Notificações/contratos -
Modelo</u>**

***<u>Para retorno de notificações/contratos:</u>***

{

"credor":\[{

> "nome":"string",
>
> "cnpj":"string”,
>
> "endereco":"string”,
>
> "email":"string”,
>
> "telefone_contato":"string”,
>
> "inscricao_estadual":"string”

}\],

"devedor":\[{

> "nome":"string",
>
> "cpf_cnpj":"string”
>
> "enderecos":\[{
>
> “endereco":"string”
>
> }\],
>
> "contatos_email":\[{
>
> "email":"string”
>
> }\],
>
> "contato_telefones":\[{
>
> "telefone":"string”
>
> }\],

}\],

"garantidor":\[{

> "nome":"string",
>
> "cpf_cnpj":"string”

}\],

"veiculos":\[{

> "chassi":"string”,
>
> "renavam":"string”,
>
> "gravame":"string”,
>
> "placa":"string”,
>
> "marca_modelo":"string”,
>
> "ano_fabricacao":"inteiro”,
>
> "ano_modelo":"inteiro”,

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./py2v5fuo.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460
>
> "cor":"string”,
>
> "uf_emplacamento":"string”,
>
> "registro_detran":"string”,
>
> "possui_gps":"string”

}\],

"contrato":\[{

> "numero":"string”,
>
> "protocolo":"string”,
>
> "data_contrato":"data”,
>
> "data_pedido":"data”,
>
> "data_notificacao":"data”,
>
> "data_decurso_prazo":"data”,
>
> "municipio_contrato":"string”,
>
> "certidao_busca_apreensao":"blob”,
>
> "valor_dívida":"string””,
>
> "valor_leilao":"string”,
>
> "taxa_juros":"string”,
>
> "valor_parcela":"string”,
>
> "quantidade_parcelas_pagas":"inteiro””,
>
> "quantidade_parcelas_abertas":"inteiro”,
>
> "data_primeira_parcela":"data”,
>
> "descrição":"string”,
>
> “protocolo_b&a”:”String”,
>
> “data_certidao”:”data”,
>
> “data_restricao”:”data”,
>
> “numero_restricao”:”String”,
>
> “data_baixa_restricao”:”data”,
>
> “nsu”:”String”

}\],

"serventia":\[{

> "cns":"inteiro””,
>
> "nome":"string”,
>
> "endereço":"data”,
>
> "titular":"string”,
>
> "substituto":"string”

}\],

"detran":\[{

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./syixslec.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460
>
> "sigla_UF":"string””,
>
> "nome_detran":"string”

}\],

"orgao_escob":\[{

> "nome":"string",
>
> "cnpj":"string”,
>
> "endereco":"string”,
>
> "email":"string”,
>
> "telefone_contato":"string”,

}\],

"orgao_guincho":\[{

> "nome":"string",
>
> "cnpj":"string”,
>
> "endereco":"string”,
>
> "email":"string”,
>
> "telefone_contato":"string”,

}\],

"orgao_despachante":\[{

> "nome":"string",
>
> "cnpj":"string”,
>
> "endereco":"string”,
>
> "email":"string”,
>
> "telefone_contato":"string”,

}\],

"orgao_leilao":\[{

> "nome":"string",
>
> "cnpj":"string”,
>
> "endereco":"string”,
>
> "email":"string”,
>
> "telefone_contato":"string”,

}\],

"orgao_localizador":\[{

> "nome":"string",
>
> "cnpj":"string”,
>
> "endereco":"string”,
>
> "email":"string”,

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./ged1e0wp.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460
>
> "telefone_contato":"string”,

}\],

"orgao_patio":\[{

> "nome":"string",
>
> "cnpj":"string”,
>
> "endereco":"string”,
>
> "email":"string”,
>
> "telefone_contato":"string”,

}\],

"notificacao_eletronica":\[{

> "forma_comunicacao":"string",
>
> "data_envio":"data”,
>
> "data_leitura":"data”,
>
> "arquivo_evidencia":"blob”

}\],

"notificacao_via_ar":\[{

> "forma_comunicacao":"string",
>
> "data_envio":"data”,
>
> "data_leitura":"data”,
>
> "arquivo_evidencia":"blob”

}\]

}

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./cbmgs4gb.png"
> style="width:7.35611in;height:0.55208in" /><img src="./cfkaujgn.png"
> style="width:2.55889in;height:5.12569in" /><img src="./nxbya42e.png"
> style="width:2.95208in;height:3.21181in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460

||
||
||
||

> Classificação de confidencialidade:
> Restrito<img src="./00lxn55w.png"
> style="width:7.26805in;height:4.50833in" />
>
> <img src="./oblbo1rw.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460

**<u>Solicitação de Consulta – Busca de Notificações/contratos -
RETORNO</u>**

O layout do JSON com as informações relativas ao retorno das informações
da solicitação se encontra abaixo.

||
||
||
||
||
||
||
||
||

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./ga0xnnoi.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460

||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./txh4qrdi.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460

||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./c504ksw0.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460

||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||
||

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./gszkzesj.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460

||
||
||

**<u>Solicitação de Consulta por período de notificações canceladas –
Consulta por período</u>**

O layout do JSON com as informações relativas à obrigatoriedade de
preenchimento e tamanho limite das informações se encontra abaixo.

OBS: a relação abaixo indica o tipo de dado final. Todas as informações
podem ser enviadas como String, conforme o exemplo. Todos os campos são
obrigatórios.

||
||
||
||
||

> **Rota:**
> **/api/recepcaoContrato/cancelados/periodo/{data_inicio}/{data_fim}**
>
> **Exemplo:**
> **/api/recepcaoContrato/cancelados/periodo/2024-10-01/2024-12-31**
>
> **Descrição**: Método a ser chamado para iniciar a consulta por
> período de notificações canceladas
>
> Parâmetros: token jwt, JSON descrito acima
>
> Retorno: JSON descrito a seguir.

**<u>Solicitação de Consulta por período de notificações canceladas -
Modelo</u>**

***<u>Para consulta:</u>***

{

> "data_inicio": "2024-08-15", "data_fim": "2024-08-16"

<img src="./niq2naqc.png"
style="width:3.55347in;height:1.74153in" />}

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./kqjskhez.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460

**<u>Resultado de Consulta por período de notificações canceladas –
Consulta por período</u>**

O layout do JSON com as informações relativas à obrigatoriedade de
preenchimento e tamanho limite das informações se encontra abaixo.

OBS: a relação abaixo indica o tipo de dado final. Todas as informações
podem ser enviadas como String, conforme o exemplo. Pelo menos uma é
obrigatória.

||
||
||
||
||
||
||
||
||
||
||
||
||

**<u>Resultado de Consulta por período de notificações canceladas -
Modelo</u>**

{

> "nome_credor": "Banco Bradesco Financiamentos", "data_pedido":
> "2024-07-26 13:51:38.029684", "numero_contrato": "ABC123456",
>
> "etapa": "Aguardar prazo legal", "data_movimentacao": "2024-07-26
> 13:58:23", "situação_pedido": "cancelado", "descrição_cancelamento":
> "Dívida quitada", "veiculos": \[
>
> {
>
> "uf_emplacamento": "BA", "placa": "JLB2759",
>
> "modelo": "Palio HLX 1.8 mpi Flex 8V 4p" },
>
> {
>
> "uf_emplacamento": "BA", "placa": "JLB7777",
>
> "modelo": "HB20 Comfort Plus 1.0 8V 4p" }

\] }

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./jfu0kxm4.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460

**<u>Tabela de Mensagens de Erro Montreal/RJ de consulta:</u>**

||
||
||
||
||
||
||
||
||
||
||
||
||
||
||

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./voplbcfa.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460

**<u>URL para acesso ao ambiente de homologação</u>**

Documentação do Swagger

**https://homol-recupera.montreal.com.br/api/documentation#/**

Autenticação/Login

**Rota:** **/api/sanctum/token**

Consulta por período

**Rota:** **/api/recepcaoContrato/periodo/{data_inicio}/{data_fim}**

Busca de notificações

**Rota:** **/api/recepcaoContrato/receber**

Busca de notificações canceladas

**Rota:** **/api/recepcaoContrato/cancelados/periodo**

||
||
||
||

> Classificação de confidencialidade: Restrito
>
> <img src="./3bxbxqia.png"
> style="width:7.35611in;height:0.55208in" />Av. Barão de Tefé nº 7 - 4º
> e 5º andar - Centro - CEP: 20.220-460

**<u>Tipos de Dados dos JSON enviados e recebidos</u>**

||
||
||
||
||
||
||
||

**<u>Críticas nos dados enviados</u>**

> • A aplicação critica o **Tamanho** **limite** de cada campo. Observem
> os tamanhos limites informados
>
> • É possível enviar o formato **string** começando com zero, sem
> causar erro, exemplo: "01".
>
> • É possível enviar um valor inválido sem causar erro inesperado,
> exemplo: "SP". No entanto haverá crítica e a consulta não será aceita.
>
> • É possível enviar um valor que não é citado na documentação, sem
> causar erro inesperado, exemplo: "25". No entanto haverá crítica e a
> consulta não será aceita.
>
> • Valores **numéricos** considerados inválidos no formato JSON causam
> erro inesperado, exemplo: 05 (sem aspas).
>
> • Valores **numéricos** considerados válidos no formato JSON são
> aceitos: 5 (sem aspas). • Os valores considerados válidos para
> **cada** **campo** estão previamente na
>
> documentação.

**<u>Outras regras operacionais a serem consideradas</u>**

> • As serventias exigem pagamento de emolumentos para realização da
> intimação/notificação no cartório. Ocredor pode pagar sua própriataxa
> ou a Montreal pode suprir esta informação em campo correspondente.
> Isso deve ser previamente acordado.

||
||
||
||
