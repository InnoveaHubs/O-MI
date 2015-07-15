// Generated by CoffeeScript 1.9.3
(function() {
  var requestsExt;

  requestsExt = function(WebOmi) {
    var currentParams, my;
    my = WebOmi.requests = {};
    my.xmls = {
      readAll: "<?xml version=\"1.0\"?>\n<omi:omiEnvelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:omi=\"omi.xsd\"\n    version=\"1.0\" ttl=\"0\">\n  <omi:read msgformat=\"odf\">\n    <omi:msg xmlns=\"odf.xsd\" xsi:schemaLocation=\"odf.xsd odf.xsd\">\n      <Objects></Objects>\n    </omi:msg>\n  </omi:read>\n</omi:omiEnvelope>",
      templateMsg: "<?xml version=\"1.0\"?>\n<omi:omiEnvelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:omi=\"omi.xsd\"\n    version=\"1.0\" ttl=\"0\">\n  <omi:read msgformat=\"odf\">\n    <omi:msg xmlns=\"odf.xsd\" xsi:schemaLocation=\"odf.xsd odf.xsd\">\n    </omi:msg>\n  </omi:read>\n</omi:omiEnvelope>\n",
      template: "<?xml version=\"1.0\"?>\n<omi:omiEnvelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:omi=\"omi.xsd\"\n    version=\"1.0\" ttl=\"0\">\n  <omi:read msgformat=\"odf\">\n    <omi:msg xmlns=\"odf.xsd\" xsi:schemaLocation=\"odf.xsd odf.xsd\">\n    </omi:msg>\n  </omi:read>\n</omi:omiEnvelope>\n"
    };
    my.defaults = {};
    my.defaults.empty = function() {
      return {
        request: null,
        ttl: 0,
        callback: null,
        requestID: null,
        odf: null,
        interval: null,
        newest: null,
        oldest: null,
        begin: null,
        end: null,
        requestDoc: null,
        msg: true
      };
    };
    my.defaults.readAll = function() {
      return $.extend({}, my.defaults.empty(), {
        request: "read",
        odf: ["Objects"],
        requestDoc: WebOmi.omi.parseXml(my.xmls.readAll)
      });
    };
    my.defaults.readOnce = function() {
      return $.extend({}, my.defaults.empty(), {
        request: "read",
        odf: ["Objects"]
      });
    };
    my.defaults.subscription = function() {
      return $.extend({}, my.defaults.empty(), {
        request: "read",
        interval: 5,
        ttl: 60,
        odf: ["Objects"]
      });
    };
    my.defaults.poll = function() {
      return $.extend({}, my.defaults.empty(), {
        request: "read",
        requestID: 1,
        msg: false
      });
    };
    my.defaults.write = function() {
      return $.extend({}, my.defaults.empty(), {
        request: "write",
        odf: ["Objects"]
      });
    };
    my.defaults.cancel = function() {
      return $.extend({}, my.defaults.empty(), {
        request: "cancel",
        requestID: 1,
        odf: null,
        msg: false
      });
    };
    currentParams = my.defaults.empty;
    my.confirmOverwrite = function(oldVal, newVal) {
      return confirm("You have edited the request manually.\n Do you want to overwrite " + oldVal.toString + " with " + newVal.toString);
    };
    my.update = {
      request: function(reqName, userDoc) {
        if (currentParams.request == null) {
          return my.loadParams(my.defaults[reqName]);
        } else {
          if (userDoc != null) {

          } else {

          }
        }
      },
      ttl: 0,
      callback: null,
      requestID: null,
      odf: null,
      interval: null,
      newest: null,
      oldest: null,
      begin: null,
      end: null,
      msg: true
    };
    my.generate = function() {
      return formLogic.setRequest(cp.resultDoc);
    };
    my.forceGenerate = function() {
      var cp, key, o, ref, results, updateFn;
      o = WebOmi.omi;
      cp = currentParams;
      if ((cp.request != null) && cp.request.length > 0 && (cp.ttl != null)) {
        cp.requestDoc = o.parseXml(my.xmls.empty);
      }
      ref = my.update;
      results = [];
      for (key in ref) {
        updateFn = ref[key];
        results.push(updateFn(cp[key]));
      }
      return results;
    };
    my.forceLoadParams = function(omiRequestObject) {
      var key, newVal;
      for (key in omiRequestObject) {
        newVal = omiRequestObject[key];
        currentParams[key] = newVal;
        WebOmi.consts.ui[key].set(newVal);
      }
      return my.forceGenerate();
    };
    my.readAll = function(fastForward) {
      WebOmi.formLogic.setRequest(my.xmls.readAll);
      if (fastForward) {
        return WebOmi.formLogic.send(WebOmi.formLogic.buildOdfTreeStr);
      }
    };
    my.addPathToRequest = function(path) {
      var fl, o, odfTreeNode;
      o = WebOmi.omi;
      fl = WebOmi.formLogic;
      odfTreeNode = $(jqesc(path));
      return fl.modifyRequestOdfs(function(currentObjectsHead) {
        var objects;
        if (currentObjectsHead != null) {
          return my.addPathToOdf(odfTreeNode, currentObjectsHead);
        } else {
          objects = o.createOdfObjects(xmlTree);
          my.addPathToOdf(odfTreeNode, objects);
          return msg.appendChild(objects);
        }
      });
    };
    my.removePathFromRequest = function(path) {
      var fl, o, odfTreeNode;
      o = WebOmi.omi;
      fl = WebOmi.formLogic;
      odfTreeNode = $(jqesc(path));
      return fl.modifyRequestOdfs(function(odfObjects) {
        return my.removePathFromOdf(odfTreeNode, odfObjects);
      });
    };
    my.removePathFromOdf = function(odfTreeNode, odfObjects) {
      var allOdfElems, elem, i, id, lastOdfElem, len, maybeChild, node, nodeElems, o;
      o = WebOmi.omi;
      nodeElems = $.makeArray(odfTreeNode.parentsUntil("#Objects", "li"));
      nodeElems.reverse();
      nodeElems.push(odfTreeNode);
      lastOdfElem = odfObjects;
      allOdfElems = (function() {
        var i, len, results;
        results = [];
        for (i = 0, len = nodeElems.length; i < len; i++) {
          node = nodeElems[i];
          id = $(node).children("a").text();
          maybeChild = o.getOdfChild(id, lastOdfElem);
          if (maybeChild != null) {
            lastOdfElem = maybeChild;
          }
          results.push(maybeChild);
        }
        return results;
      })();
      lastOdfElem.parentElement.removeChild(lastOdfElem);
      allOdfElems.pop();
      allOdfElems.reverse();
      for (i = 0, len = allOdfElems.length; i < len; i++) {
        elem = allOdfElems[i];
        if (!o.hasOdfChildren(elem)) {
          elem.parentElement.removeChild(elem);
        }
      }
      return odfObjects;
    };
    my.addPathToOdf = function(odfTreeNode, odfObjects) {
      var currentOdfNode, i, id, len, maybeChild, node, nodeElems, o, obj, odfDoc;
      o = WebOmi.omi;
      odfDoc = odfObjects.ownerDocument || odfObjects;
      nodeElems = $.makeArray(odfTreeNode.parentsUntil("#Objects", "li"));
      nodeElems.reverse();
      nodeElems.push(odfTreeNode);
      currentOdfNode = odfObjects;
      for (i = 0, len = nodeElems.length; i < len; i++) {
        node = nodeElems[i];
        id = $(node).children("a").text();
        maybeChild = o.getOdfChild(id, currentOdfNode);
        if (maybeChild != null) {
          currentOdfNode = maybeChild;
        } else {
          obj = (function() {
            switch (WebOmi.consts.odfTree.get_type(node)) {
              case "object":
                return o.createOdfObject(odfDoc, id);
              case "infoitem":
                return o.createOdfInfoItem(odfDoc, id);
            }
          })();
          currentOdfNode.appendChild(obj);
          currentOdfNode = obj;
        }
      }
      return odfObjects;
    };
    return WebOmi;
  };

  window.WebOmi = requestsExt(window.WebOmi || {});

}).call(this);
