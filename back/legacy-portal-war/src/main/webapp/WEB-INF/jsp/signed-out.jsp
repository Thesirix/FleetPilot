<%-- After the sign-out. --%>
<c:set var="title" value="Déconnecté"/>
<%@ include file="/WEB-INF/jsp/fragments/header.jspf" %>
<section class="card notice">
    <h1>Vous êtes déconnecté</h1>
    <p>Votre session de l'extranet et votre session FleetPilot sont fermées.</p>
    <p><a class="btn btn-primary" href="${pageContext.request.contextPath}/">Se reconnecter</a></p>
</section>
<%@ include file="/WEB-INF/jsp/fragments/footer.jspf" %>
