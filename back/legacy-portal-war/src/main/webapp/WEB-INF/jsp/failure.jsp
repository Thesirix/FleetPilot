<%-- Unexpected error (error page of the container): no technical detail is shown to the lessor. --%>
<c:set var="title" value="Erreur"/>
<%@ include file="/WEB-INF/jsp/fragments/header.jspf" %>
<section class="card notice">
    <h1>Une erreur est survenue</h1>
    <p>L'équipe FleetPilot est prévenue. Réessayez dans quelques minutes.</p>
    <p><a class="btn btn-primary" href="${pageContext.request.contextPath}/">Retour aux contrats</a></p>
</section>
<%@ include file="/WEB-INF/jsp/fragments/footer.jspf" %>
