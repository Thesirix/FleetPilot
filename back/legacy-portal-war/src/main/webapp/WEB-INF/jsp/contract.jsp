<%-- A contract, the readings of its vehicle and the form of a reading. Model: contract (ContractRow), readings,
     lastMileage, now, flash, csrf, user. --%>
<c:set var="title" value="${contract.contractNumber()}"/>
<c:set var="base" value="${pageContext.request.contextPath}"/>
<%@ include file="/WEB-INF/jsp/fragments/header.jspf" %>

<a class="back" href="${base}/">&larr; Contrats en cours</a>
<div class="page-header">
    <div>
        <h1 class="mono"><c:out value="${contract.contractNumber()}"/></h1>
        <p class="muted"><span class="plate"><c:out value="${contract.registration()}"/></span>
            <c:out value="${contract.vehicle()}"/></p>
    </div>
    <c:if test="${contract.overrunRisk()}">
        <span class="badge warn big">Dépassement prévu <c:out value="${contract.projectedOverrun()}"/></span>
    </c:if>
</div>

<c:if test="${not empty flash}">
    <p class="flash ${flash.kind()}" role="status"><c:out value="${flash.text()}"/></p>
</c:if>

<div class="columns">
    <section class="card">
        <h2>Contrat</h2>
        <dl class="dl">
            <dt>Début</dt><dd><c:out value="${contract.startDate()}"/></dd>
            <dt>Fin</dt><dd><c:out value="${contract.endDate()}"/>
                <c:if test="${not empty contract.remaining()}"><span class="muted">&nbsp;(<c:out value="${contract.remaining()}"/>)</span></c:if></dd>
            <dt>Durée</dt><dd><c:out value="${contract.duration()}"/></dd>
            <dt>Kilométrage contractuel</dt><dd><c:out value="${contract.contractualMileage()}"/></dd>
            <dt>Projeté en fin de contrat</dt><dd><c:out value="${contract.projectedMileage()}"/></dd>
            <dt>Loyer mensuel</dt><dd><c:out value="${contract.monthlyRent()}"/></dd>
            <dt>Prestations incluses</dt><dd><c:out value="${contract.services()}"/></dd>
        </dl>
    </section>

    <section class="card">
        <h2>Saisir un relevé kilométrique</h2>
        <p class="muted small">À la restitution du véhicule ou lors d'une expertise. Le dernier relevé connu est de
            <strong><c:out value="${lastMileage}"/></strong>.</p>
        <form method="post" action="${base}/reading" class="form">
            <input type="hidden" name="_csrf" value="${csrf}">
            <input type="hidden" name="contract" value="${contract.id()}">
            <input type="hidden" name="vehicle" value="${contract.vehicleId()}">
            <div class="field">
                <label for="mileage">Kilométrage au compteur</label>
                <input id="mileage" name="mileage" class="input" inputmode="numeric" required autocomplete="off">
            </div>
            <div class="field">
                <label for="readAt">Date et heure du relevé</label>
                <input id="readAt" name="readAt" type="datetime-local" class="input" value="${now}" max="${now}">
            </div>
            <button type="submit" class="btn btn-primary">Enregistrer le relevé</button>
        </form>
    </section>
</div>

<section class="card">
    <h2>Relevés du véhicule</h2>
    <div class="table-wrap">
        <table class="table">
            <thead><tr><th>Date</th><th class="right">Kilométrage</th><th>Source</th></tr></thead>
            <tbody>
            <c:forEach var="reading" items="${readings}">
                <tr>
                    <td><c:out value="${reading.readAt()}"/></td>
                    <td class="right num"><c:out value="${reading.mileage()}"/></td>
                    <td><span class="badge ${reading.source() == 'Loueur' ? 'info' : ''}"><c:out value="${reading.source()}"/></span></td>
                </tr>
            </c:forEach>
            <c:if test="${empty readings}">
                <tr><td colspan="3" class="empty">Aucun relevé pour ce véhicule.</td></tr>
            </c:if>
            </tbody>
        </table>
    </div>
</section>

<%@ include file="/WEB-INF/jsp/fragments/footer.jspf" %>
