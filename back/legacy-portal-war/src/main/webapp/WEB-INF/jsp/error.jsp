<%-- Something went wrong: the message for the lessor, and a way out. Model: message, logoutUrl (optional). --%>
<c:set var="title" value="Erreur"/>
<%@ include file="/WEB-INF/jsp/fragments/header.jspf" %>
<section class="card notice">
    <h1>Impossible d'afficher cette page</h1>
    <p><c:out value="${message}"/></p>
    <p>
        <c:choose>
            <c:when test="${not empty logoutUrl}">
                <a class="btn btn-primary" href="${fn:escapeXml(logoutUrl)}">Se connecter avec un autre compte</a>
            </c:when>
            <c:otherwise>
                <a class="btn btn-primary" href="${pageContext.request.contextPath}/">Retour aux contrats</a>
            </c:otherwise>
        </c:choose>
    </p>
</section>
<%@ include file="/WEB-INF/jsp/fragments/footer.jspf" %>
