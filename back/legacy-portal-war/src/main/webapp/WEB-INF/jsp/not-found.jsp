<%-- Unknown address (error page of the container). --%>
<c:set var="title" value="Page introuvable"/>
<%@ include file="/WEB-INF/jsp/fragments/header.jspf" %>
<section class="card notice">
    <h1>Page introuvable</h1>
    <p>Cette adresse ne correspond à aucune page de l'extranet.</p>
    <p><a class="btn btn-primary" href="${pageContext.request.contextPath}/">Retour aux contrats</a></p>
</section>
<%@ include file="/WEB-INF/jsp/fragments/footer.jspf" %>
