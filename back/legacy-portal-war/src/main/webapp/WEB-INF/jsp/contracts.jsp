<%-- Home page: the active contracts of the lessor. Model: list (ContractList), flash, csrf, user. --%>
<c:set var="title" value="Mes contrats"/>
<c:set var="base" value="${pageContext.request.contextPath}"/>
<%-- Links keep the filter and the search, URL-encoded by c:url --%>
<c:url var="exportUrl" value="/contracts.csv"><c:param name="filter" value="${list.filter()}"/><c:param name="q" value="${list.search()}"/></c:url>
<c:url var="previousUrl" value="/"><c:param name="filter" value="${list.filter()}"/><c:param name="q" value="${list.search()}"/><c:param name="page" value="${list.page() - 1}"/></c:url>
<c:url var="nextUrl" value="/"><c:param name="filter" value="${list.filter()}"/><c:param name="q" value="${list.search()}"/><c:param name="page" value="${list.page() + 1}"/></c:url>
<%@ include file="/WEB-INF/jsp/fragments/header.jspf" %>

<div class="page-header">
    <div>
        <h1>Contrats en cours</h1>
        <p class="muted">Les contrats de location longue durée que <c:out value="${user.lessor()}"/> porte pour les clients
            de FleetPilot, avec le kilométrage projeté en fin de contrat d'après l'usage réel des véhicules.</p>
    </div>
    <a class="btn" href="${fn:escapeXml(exportUrl)}">Exporter en CSV</a>
</div>

<c:if test="${not empty flash}">
    <p class="flash ${flash.kind()}" role="status"><c:out value="${flash.text()}"/></p>
</c:if>

<section class="kpis">
    <a class="kpi ${list.filter() == 'all' ? 'active' : ''}" href="${base}/?filter=all">
        <span class="kpi-label">Contrats en cours</span>
        <strong class="kpi-value"><c:out value="${list.active()}"/></strong>
    </a>
    <a class="kpi ${list.filter() == 'returns' ? 'active' : ''}" href="${base}/?filter=returns">
        <span class="kpi-label">Restitutions sous 90 jours</span>
        <strong class="kpi-value"><c:out value="${list.returnsSoon()}"/></strong>
    </a>
    <a class="kpi warn ${list.filter() == 'overrun' ? 'active' : ''}" href="${base}/?filter=overrun">
        <span class="kpi-label">Dépassement kilométrique prévu</span>
        <strong class="kpi-value"><c:out value="${list.overrunRisk()}"/></strong>
        <span class="kpi-hint">soit <c:out value="${list.projectedOverrun()}"/> au total</span>
    </a>
</section>

<section class="card">
    <form class="toolbar" method="get" action="${base}/">
        <input type="hidden" name="filter" value="${list.filter()}">
        <label class="sr-only" for="q">Rechercher</label>
        <input id="q" name="q" type="search" class="input" value="${fn:escapeXml(list.search())}"
               placeholder="Immatriculation ou numéro de contrat">
        <button class="btn" type="submit">Rechercher</button>
        <span class="muted small"><c:out value="${list.matching()}"/> contrat(s)</span>
    </form>

    <div class="table-wrap">
        <table class="table">
            <thead>
            <tr>
                <th>Contrat</th>
                <th>Véhicule</th>
                <th>Fin</th>
                <th class="right">Km contractuels</th>
                <th class="right">Km projetés</th>
                <th class="right">Dépassement</th>
                <th class="right">Loyer</th>
            </tr>
            </thead>
            <tbody>
            <c:forEach var="row" items="${list.rows()}">
                <tr>
                    <td><a class="mono" href="${base}/contract?id=${row.id()}"><c:out value="${row.contractNumber()}"/></a></td>
                    <td><span class="plate"><c:out value="${row.registration()}"/></span>
                        <span class="muted small"><c:out value="${row.vehicle()}"/></span></td>
                    <td class="nowrap"><c:out value="${row.endDate()}"/>
                        <c:if test="${row.returnSoon()}"><span class="badge info"><c:out value="${row.remaining()}"/></span></c:if></td>
                    <td class="right num"><c:out value="${row.contractualMileage()}"/></td>
                    <td class="right num"><c:out value="${row.projectedMileage()}"/></td>
                    <td class="right num">
                        <c:if test="${row.overrunRisk()}"><span class="badge warn"><c:out value="${row.projectedOverrun()}"/></span></c:if>
                    </td>
                    <td class="right num"><c:out value="${row.monthlyRent()}"/></td>
                </tr>
            </c:forEach>
            <c:if test="${empty list.rows()}">
                <tr><td colspan="7" class="empty">Aucun contrat ne correspond.</td></tr>
            </c:if>
            </tbody>
        </table>
    </div>

    <c:if test="${list.pages() > 1}">
        <nav class="pager" aria-label="Pages">
            <c:if test="${list.page() > 1}">
                <a class="btn" href="${fn:escapeXml(previousUrl)}">Précédente</a>
            </c:if>
            <span class="muted small">Page <c:out value="${list.page()}"/> sur <c:out value="${list.pages()}"/></span>
            <c:if test="${list.page() < list.pages()}">
                <a class="btn" href="${fn:escapeXml(nextUrl)}">Suivante</a>
            </c:if>
        </nav>
    </c:if>
</section>

<%@ include file="/WEB-INF/jsp/fragments/footer.jspf" %>
