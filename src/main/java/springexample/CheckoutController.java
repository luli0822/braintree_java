package springexample;

import com.braintreegateway.*;
import com.braintreegateway.Transaction.Status;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.Arrays;

@Controller
public class CheckoutController {
    private Logger logger = Logger.getLogger(CheckoutController.class);
    private static final String REDIRECT_CHECKOUTS_URI = "redirect:checkouts";
    private BraintreeGateway gateway = Application.gateway;

     private Status[] TRANSACTION_SUCCESS_STATUSES = new Status[] {
        Status.AUTHORIZED,
        Status.AUTHORIZING,
        Status.SETTLED,
        Status.SETTLEMENT_CONFIRMED,
        Status.SETTLEMENT_PENDING,
        Status.SETTLING,
        Status.SUBMITTED_FOR_SETTLEMENT
     };

    @GetMapping(value = "/")
    public String root(Model model) {
        return REDIRECT_CHECKOUTS_URI;
    }

    @GetMapping(value = "/checkouts")
    public String checkout(Model model) {
        String clientToken = gateway.clientToken().generate();
        model.addAttribute("clientToken", clientToken);

        return "checkouts/new";
    }

    @PostMapping(value = "/checkouts")
    public String postForm(@RequestParam("amount") String amount, @RequestParam("payment_method_nonce") String nonce, Model model, final RedirectAttributes redirectAttributes) {
        BigDecimal decimalAmount;
        try {
            decimalAmount = new BigDecimal(amount);
        } catch (NumberFormatException e) {
            redirectAttributes.addFlashAttribute("errorDetails", "Error: 81503: Amount is an invalid format.");
            return REDIRECT_CHECKOUTS_URI;
        }

        TransactionRequest request = new TransactionRequest()
            .amount(decimalAmount)
            .paymentMethodNonce(nonce)
            .options()
                .submitForSettlement(true)
                .done();

        Result<Transaction> result = gateway.transaction().sale(request);

        if (result.isSuccess()) {
            Transaction transaction = result.getTarget();
            return REDIRECT_CHECKOUTS_URI + "/" + transaction.getId();
        } else if (result.getTransaction() != null) {
            Transaction transaction = result.getTransaction();
            return REDIRECT_CHECKOUTS_URI + "/" + transaction.getId();
        } else {
            StringBuilder errorBuilder = new StringBuilder();
            for (ValidationError error : result.getErrors().getAllDeepValidationErrors()) {
                errorBuilder.append("Error: ")
                            .append(error.getCode())
                            .append(": ")
                            .append(error.getMessage())
                            .append("\n");
            }
            redirectAttributes.addFlashAttribute("errorDetails", errorBuilder.toString());
            return REDIRECT_CHECKOUTS_URI;
        }
    }

    @GetMapping(value = "/checkouts/{transactionId}")
    public String getTransaction(@PathVariable String transactionId, Model model) {
        Transaction transaction;
        CreditCard creditCard;
        Customer customer;

        try {
            transaction = gateway.transaction().find(transactionId);
            creditCard = transaction.getCreditCard();
            customer = transaction.getCustomer();
        } catch (Exception e) {
            logger.error(e);
            return REDIRECT_CHECKOUTS_URI;
        }

        model.addAttribute("isSuccess", Arrays.asList(TRANSACTION_SUCCESS_STATUSES).contains(transaction.getStatus()));
        model.addAttribute("transaction", transaction);
        model.addAttribute("creditCard", creditCard);
        model.addAttribute("customer", customer);

        return "checkouts/show";
    }
}
